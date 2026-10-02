// The trading account: pretend cash, real prices.
//
// The owner starts with RULES.startCash and buys and sells securities at the
// exchange's own prices. Nothing here takes a price, a quantity held or a date from a
// client — a client says only "buy 10 of 629014", and the price is read from the
// exchange by this server at that moment. So what the account shows can only be what
// the market would actually have done.
//
// Two ways an order fills, both what a real broker does with a market order:
//   • the session is live and the paper has traded today → at its last price, now;
//   • otherwise (closed, pre-open, or not traded yet today) → it waits, and fills at the
//     OPENING price of the first trading day it is allowed to (`fillFrom`). That price
//     is known exactly once the paper opens, however late the account is next read.
//
// Money is kept in whole agorot. Holdings are derived from the trades, never stored.

import { randomUUID } from 'node:crypto';
import { rename } from 'node:fs/promises';
import type { TradeQuote } from './market';
import { StoreError, dataFile, readText, serial, writeJson } from './store.ts';

export const RULES = {
  /** ₪100,000, in agorot. */
  startCash: 10_000_000,
  /** Commission: 0.1% of the deal, at least ₪5 — a typical Israeli online broker. */
  feeRate: 0.001,
  feeMin: 500,
  maxQty: 10_000_000,
} as const;

export type Side = 'buy' | 'sell';

export type Paper = { id: string; name: string; symbol: string | null; type: string | null; companyId: string | null };

export type Order = {
  id: string;
  side: Side;
  paper: Paper;
  qty: number;
  placedAt: string;
  /** Israel date (YYYY-MM-DD): the first trading day whose opening price may fill it. */
  fillFrom: string;
  status: 'pending' | 'filled' | 'cancelled' | 'rejected';
  /** Why it was rejected; null otherwise. */
  reason: string | null;
  /** Agorot held back from the cash while a buy waits: its value at the last price, plus the fee. */
  reserve: number;
  closedAt: string | null;
};

export type Trade = {
  id: string;
  orderId: string;
  side: Side;
  paper: Paper;
  qty: number;
  /** Agorot per unit, as the exchange quoted it. */
  price: number;
  /** qty × price, agorot. */
  gross: number;
  fee: number;
  at: string;
  tradeDate: string | null;
  /** 'live' = the last price during the session; 'open' = the opening price. */
  how: 'live' | 'open';
};

export type Account = { rev: number; startedAt: string; startCash: number; cash: number; orders: Order[]; trades: Trade[] };

export type Position = {
  paper: Paper;
  qty: number;
  /** What the units still held cost, fees included, agorot. */
  cost: number;
  /** Agorot per unit. */
  avgCost: number;
};

// ── money and dates ─────────────────────────────────────────────────────────

export const grossOf = (qty: number, price: number) => Math.round(qty * price);
export const feeOf = (gross: number) => Math.max(RULES.feeMin, Math.ceil(gross * RULES.feeRate));

const ISRAEL = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Jerusalem', year: 'numeric', month: '2-digit', day: '2-digit' });
export const israelDate = (d: Date) => ISRAEL.format(d);

function nextDay(iso: string): string {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}

// ── what is held ────────────────────────────────────────────────────────────

/** Replay the trades: average-cost holdings, and what selling has realised so far. */
export function positions(trades: Trade[]): { positions: Position[]; realized: number; fees: number } {
  const held = new Map<string, Position>();
  let realized = 0;
  let fees = 0;
  for (const t of [...trades].sort((a, b) => a.at.localeCompare(b.at))) {
    fees += t.fee;
    const p = held.get(t.paper.id) ?? { paper: t.paper, qty: 0, cost: 0, avgCost: 0 };
    if (t.side === 'buy') {
      p.qty += t.qty;
      p.cost += t.gross + t.fee;
    } else {
      const out = p.qty > 0 ? (p.cost * t.qty) / p.qty : 0;
      realized += t.gross - t.fee - out;
      p.qty -= t.qty;
      p.cost -= out;
    }
    p.paper = t.paper;
    p.avgCost = p.qty > 0 ? p.cost / p.qty : 0;
    if (p.qty > 0) held.set(t.paper.id, p);
    else held.delete(t.paper.id);
  }
  return { positions: [...held.values()], realized, fees };
}

const pending = (a: Account) => a.orders.filter((o) => o.status === 'pending');
const reserved = (a: Account, except?: string) => pending(a).reduce((s, o) => s + (o.side === 'buy' && o.id !== except ? o.reserve : 0), 0);
const heldQty = (a: Account, id: string) => positions(a.trades).positions.find((p) => p.paper.id === id)?.qty ?? 0;
const pendingSells = (a: Account, id: string, except?: string) =>
  pending(a).reduce((s, o) => s + (o.side === 'sell' && o.paper.id === id && o.id !== except ? o.qty : 0), 0);

/** Cash free for a new buy: what is there, less what waiting buys hold back. */
export const available = (a: Account) => a.cash - reserved(a);

// ── the engine (pure: account in, account out) ──────────────────────────────

export function fresh(now: Date): Account {
  return { rev: 0, startedAt: now.toISOString(), startCash: RULES.startCash, cash: RULES.startCash, orders: [], trades: [] };
}

function paperOf(q: TradeQuote): Paper {
  return { id: q.id, name: q.name, symbol: q.symbol, type: q.type, companyId: q.companyId };
}

/** Execute an order at `price`, or reject it if the cash or the units are not there at that price. */
function execute(a: Account, o: Order, price: number, how: Trade['how'], tradeDate: string | null, now: Date): Account {
  const gross = grossOf(o.qty, price);
  const fee = feeOf(gross);
  const at = now.toISOString();
  const close = (status: Order['status'], reason: string | null) => ({ ...o, status, reason, reserve: 0, closedAt: at });
  const others = a.orders.filter((x) => x.id !== o.id);

  if (o.side === 'buy' && gross + fee > a.cash - reserved(a, o.id)) {
    return { ...a, orders: [...others, close('rejected', 'לא היה מספיק מזומן בשער הביצוע')] };
  }
  if (o.side === 'sell' && o.qty > heldQty(a, o.paper.id) - pendingSells(a, o.paper.id, o.id)) {
    return { ...a, orders: [...others, close('rejected', 'אין מספיק יחידות בתיק')] };
  }
  const trade: Trade = { id: randomUUID().slice(0, 8), orderId: o.id, side: o.side, paper: o.paper, qty: o.qty, price, gross, fee, at, tradeDate, how };
  const cash = o.side === 'buy' ? a.cash - gross - fee : a.cash + gross - fee;
  return { ...a, cash, orders: [...others, close('filled', null)], trades: [...a.trades, trade] };
}

function quantity(v: unknown): number {
  if (typeof v !== 'number' || !Number.isInteger(v) || v <= 0 || v > RULES.maxQty) throw new StoreError('כמות חייבת להיות מספר שלם גדול מאפס');
  return v;
}

/**
 * A market order for `qty` units, priced by the quote this server just read. Fills now
 * when the paper is trading live; otherwise waits for the next opening price.
 */
export function place(a: Account, side: unknown, rawQty: unknown, q: TradeQuote, now: Date): { account: Account; order: Order } {
  if (side !== 'buy' && side !== 'sell') throw new StoreError('צד הפקודה חייב להיות buy או sell');
  const qty = quantity(rawQty);
  if (q.suspended) throw new StoreError('המסחר בנייר הזה מושעה כרגע', 409);

  const today = israelDate(now);
  const live = q.marketOpen && q.tradeDate === today && q.tradeTime !== null;
  // A paper that already traded today and is now closed waits for TOMORROW's opening —
  // never today's, which is already in the past.
  const fillFrom = live || q.tradeDate !== today ? today : nextDay(today);
  const estimate = grossOf(qty, q.last);
  const order: Order = {
    id: randomUUID().slice(0, 8),
    side,
    paper: paperOf(q),
    qty,
    placedAt: now.toISOString(),
    fillFrom,
    status: 'pending',
    reason: null,
    reserve: side === 'buy' ? estimate + feeOf(estimate) : 0,
    closedAt: null,
  };

  if (side === 'buy' && order.reserve > available(a)) throw new StoreError('אין מספיק מזומן פנוי לקנייה הזאת', 409);
  if (side === 'sell' && qty > heldQty(a, q.id) - pendingSells(a, q.id)) throw new StoreError('אין מספיק יחידות בתיק למכירה הזאת', 409);

  const withOrder = { ...a, orders: [...a.orders, order] };
  if (!live) return { account: withOrder, order };
  const account = execute(withOrder, order, q.last, 'live', q.tradeDate, now);
  return { account, order: account.orders.find((o) => o.id === order.id)! };
}

/** Fill every waiting order whose paper has opened on or after its `fillFrom`, oldest first. */
export function settle(a: Account, quotes: Map<string, TradeQuote>, now: Date): Account {
  let out = a;
  for (const o of [...pending(a)].sort((x, y) => x.placedAt.localeCompare(y.placedAt))) {
    const q = quotes.get(o.paper.id);
    if (!q || q.tradeDate === null || q.open === null || q.tradeDate < o.fillFrom) continue;
    out = execute(out, o, q.open, 'open', q.tradeDate, now);
  }
  return out;
}

export function cancel(a: Account, orderId: unknown, now: Date): Account {
  const o = a.orders.find((x) => x.id === orderId);
  if (!o) throw new StoreError('הפקודה לא נמצאה', 404);
  if (o.status !== 'pending') throw new StoreError('הפקודה כבר לא ממתינה', 409);
  return { ...a, orders: a.orders.map((x) => (x.id === o.id ? { ...x, status: 'cancelled' as const, reserve: 0, closedAt: now.toISOString() } : x)) };
}

// ── what a client sees ──────────────────────────────────────────────────────

export function summary(a: Account, unsettled: Array<{ id: string; error: string }> = []) {
  const { positions: held, realized, fees } = positions(a.trades);
  const newestFirst = <T extends { at?: string; closedAt?: string | null; placedAt?: string }>(xs: T[], key: (x: T) => string) => [...xs].sort((x, y) => key(y).localeCompare(key(x)));
  return {
    /** Where orders go. Only the practice account exists; a real broker would be another mode. */
    mode: 'practice' as const,
    rev: a.rev,
    startedAt: a.startedAt,
    startCash: a.startCash,
    cash: a.cash,
    available: available(a),
    positions: held,
    realized,
    fees,
    pending: newestFirst(pending(a), (o) => o.placedAt),
    /** Closed orders, newest first — filled, cancelled and rejected. */
    orders: newestFirst(
      a.orders.filter((o) => o.status !== 'pending'),
      (o) => o.closedAt ?? o.placedAt,
    ).slice(0, 100),
    trades: newestFirst(a.trades, (t) => t.at),
    /** Waiting orders whose paper could not be priced just now; they are tried again on the next read. */
    unsettled,
    rules: { feeRate: RULES.feeRate, feeMin: RULES.feeMin },
  };
}

export type Summary = ReturnType<typeof summary>;

// ── the file ────────────────────────────────────────────────────────────────

const FILE = 'account.json';
const inTurn = serial();

async function load(now: Date): Promise<Account> {
  const text = await readText(FILE);
  if (text === null) return fresh(now);
  try {
    const a = JSON.parse(text) as Account;
    if (typeof a.rev !== 'number' || typeof a.cash !== 'number' || !Array.isArray(a.orders) || !Array.isArray(a.trades)) throw new Error('wrong shape');
    return a;
  } catch (e) {
    throw new StoreError(`${FILE} is damaged (${(e as Error).message}); not touching it`, 500);
  }
}

async function save(before: Account, after: Account): Promise<Account> {
  if (after === before) return before;
  const next = { ...after, rev: before.rev + 1 };
  await writeJson(FILE, next);
  return next;
}

type Quoter = (id: string) => Promise<TradeQuote>;

/** Price every paper with a waiting order and fill what may be filled. */
async function settled(a: Account, quote: Quoter, now: Date) {
  const ids = [...new Set(pending(a).map((o) => o.paper.id))];
  const quotes = new Map<string, TradeQuote>();
  const unsettled: Array<{ id: string; error: string }> = [];
  await Promise.all(
    ids.map(async (id) => {
      try {
        quotes.set(id, await quote(id));
      } catch (e) {
        unsettled.push({ id, error: e instanceof Error ? e.message : 'שגיאה' });
      }
    }),
  );
  return { account: settle(a, quotes, now), unsettled };
}

export function readAccount(quote: Quoter, now = () => new Date()) {
  return inTurn(async () => {
    const a = await load(now());
    const s = await settled(a, quote, now());
    return summary(await save(a, s.account), s.unsettled);
  });
}

/** One operation — order, cancel, reset. Returns the account after it (and the order, for `order`). */
export function operate(op: unknown, quote: Quoter, now = () => new Date()) {
  return inTurn(async () => {
    const o = (op ?? {}) as Record<string, unknown>;
    const before = await load(now());
    // Whatever was waiting fills first, so a new order sees the cash and units as they really are.
    const s = await settled(before, quote, now());
    let after = s.account;
    let order: Order | null = null;
    switch (o.op) {
      case 'order': {
        if (typeof o.id !== 'string' || !/^\d{1,9}$/.test(o.id)) throw new StoreError('מזהה נייר לא תקין');
        const placed = place(after, o.side, o.qty, await quote(o.id), now());
        after = placed.account;
        order = placed.order;
        break;
      }
      case 'cancel':
        after = cancel(after, o.orderId, now());
        break;
      case 'reset': {
        if (o.confirm !== true) throw new StoreError('התחלה מחדש דורשת אישור');
        // The old account is kept beside the new one, never deleted.
        if ((await readText(FILE)) !== null) await rename(dataFile(FILE), dataFile(`account-until-${now().toISOString().replace(/[:.]/g, '-')}.json`));
        const next = fresh(now());
        await writeJson(FILE, next);
        return { ...summary(next), order: null };
      }
      default:
        throw new StoreError('פעולה לא מוכרת');
    }
    return { ...summary(await save(before, after), s.unsettled), order };
  });
}
