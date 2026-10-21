// The trader: a service of its own (bursa-trader-<mode>.service), separate from the web
// server so that a code reload, an idle stop or a closed browser never stops it.
//
// Every few seconds while the exchange trades it
//   1. RECORDS the day's trades of every paper that matters — the strategy's, ת"א-35's,
//      the watchlist's, what is held. The exchange keeps only today, so this record is the
//      only minute-level history there will ever be (lib/ticks.ts). The feed holds the
//      whole day until the next morning, so a paper the strategy does not trade is read
//      only now and then while the session runs, and once more after the close;
//   2. RUNS the strategy on the practice account (lib/strategy/), if the owner switched it
//      on: on each newly completed bar, one decision, one market order at most.
//
// Restart-safe by construction:
//   • each decision is written down (strategy/<date>.json) BEFORE its order is sent, so a
//     restart never decides the same bar twice; a sell lost to a crash is simply made
//     again on the next bar, because the holding is still there;
//   • each order carries its signal, and the account refuses a second order for the same
//     signal (lib/account.ts) — under the same file lock the web server takes;
//   • after a gap, only the LATEST completed bar is decided: an old signal is history,
//     not a reason to trade now.
// What it holds is never kept here: it is read back from the account's tagged trades.

import { DUPLICATE_SIGNAL, israelClock, israelDate, operate, positions, type Account, type Tag } from '../lib/account.ts';
import { dayTrades, index, isOpen, tradeQuote, type Tick } from '../lib/market.ts';
import { readList } from '../lib/list.ts';
import { readText, writeJson } from '../lib/store.ts';
import { recordDay } from '../lib/ticks.ts';
import { bars, closedBars } from '../lib/strategy/bars.ts';
import { STRATEGY, decide, params, type Holding } from '../lib/strategy/emaVwap.ts';
import { readConfig, type Config } from '../lib/strategy/config.ts';
import { readJournal, writeJournal, type Day, type Event, type TraderStatus } from '../lib/strategy/journal.ts';

const LOOP_MS = 20_000;
/** Seconds after a bar's end before it counts as complete: the feed is a little behind the trades. */
const GRACE_SEC = 20;
/** While the session runs, papers the strategy does not trade are read this often (insurance only). */
const OTHERS_EVERY_MS = 30 * 60_000;
/** Recorded whatever the owner follows, so there is always history to test on. */
const UNIVERSE_INDEX = '142'; // ת"א-35
const UNIVERSE_EVERY_MS = 60 * 60_000;

const log = (...a: unknown[]) => console.log(new Date().toISOString(), ...a);

// ── what the owner asked for ────────────────────────────────────────────────

/** A damaged strategy.json switches the strategy off — recording carries on regardless. */
async function config(): Promise<Config> {
  try {
    return await readConfig();
  } catch (e) {
    log(`strategy off: ${e instanceof Error ? e.message : e}`);
    return { rev: -1, enabled: false, papers: [], params: params({}) };
  }
}

async function account(): Promise<Account | null> {
  const text = await readText('account.json');
  return text === null ? null : (JSON.parse(text) as Account);
}

/** What the strategy holds in a paper — its own tagged trades, capped by what the account still holds. */
function holding(a: Account | null, id: string): (Holding & { qty: number }) | null {
  if (!a) return null;
  let qty = 0;
  let cost = 0;
  let date = '';
  for (const t of [...a.trades].sort((x, y) => x.at.localeCompare(y.at))) {
    if (t.paper.id !== id || t.by?.strategy !== STRATEGY) continue;
    if (t.side === 'buy') {
      if (qty === 0) date = t.tradeDate ?? t.at.slice(0, 10);
      qty += t.qty;
      cost += t.qty * t.price;
    } else {
      cost -= qty > 0 ? (cost * t.qty) / qty : 0;
      qty -= t.qty;
    }
  }
  // The owner may have sold some of it by hand; the strategy can only sell what is there.
  const held = positions(a.trades).positions.find((p) => p.paper.id === id)?.qty ?? 0;
  qty = Math.min(qty, held);
  return qty > 0 ? { qty, entry: cost / Math.max(1, qty), date } : null;
}

// ── the day's decisions ─────────────────────────────────────────────────────

async function runStrategy(id: string, ticks: Tick[], date: string, now: Date, cfg: Config, day: Day): Promise<boolean> {
  const p = cfg.params;
  const done = closedBars(bars(ticks, p.barMinutes, p.emaPeriod), israelClock(now), GRACE_SEC);
  if (done.length === 0) return false;
  const last = done.length - 1;
  const bar = done[last];
  const mine = (day.papers[id] ??= { lastBar: null, events: [] });
  if (mine.lastBar === bar.t) return false;
  if (mine.lastBar !== null) {
    const missed = last - done.findIndex((b) => b.t === mine.lastBar) - 1;
    if (missed > 0) log(`[${id}] ${missed} bar(s) went by undecided (trader was down); deciding ${bar.t} only`);
  }
  mine.lastBar = bar.t;

  const pos = holding(await account(), id);
  const d = decide(done, last, pos, date, p);
  if (!d) {
    await writeJournal(day);
    return true;
  }
  const qty = d.side === 'sell' ? pos!.qty : Math.floor(p.budget / bar.c);
  const by: Tag = { strategy: STRATEGY, signal: `${STRATEGY}:${date}:${id}:${bar.t}:${d.side}` };
  const ev: Event = { at: now.toISOString(), bar: bar.t, side: d.side, reason: d.reason, qty, signal: by.signal, orderId: null, price: null, error: null };
  mine.events.push(ev);
  // Written down before the order goes: a restart from here never decides this bar again.
  await writeJournal(day);

  if (qty <= 0) ev.error = 'התקציב קטן ממחיר יחידה אחת';
  else {
    try {
      const r = await operate({ op: 'order', side: d.side, id, qty }, tradeQuote, () => new Date(), by);
      ev.orderId = r.order?.id ?? null;
      ev.price = r.trades.find((t) => t.orderId === ev.orderId)?.price ?? null;
      if (r.order?.status !== 'filled') ev.error = r.order?.reason ?? `the order is ${r.order?.status}`;
    } catch (e) {
      ev.error = e instanceof Error ? e.message : String(e);
      if (ev.error === DUPLICATE_SIGNAL) ev.error = null; // placed before a restart — already done
    }
  }
  log(`[${id}] ${bar.t} ${d.side} ${qty} (${d.reason})`, ev.error ? `FAILED: ${ev.error}` : `filled at ${ev.price}`);
  await writeJournal(day);
  return true;
}

// ── the loop ────────────────────────────────────────────────────────────────

const lastRecorded = new Map<string, number>();
/** Papers read since the session closed — the day's record is complete for them. */
const closedPass = new Set<string>();
let universe: { at: number; ids: string[] } = { at: 0, ids: [] };
let stopping = false;

async function papersToRecord(cfg: Config): Promise<{ strategy: string[]; others: string[] }> {
  const strategy = cfg.enabled ? cfg.papers : [];
  const others = new Set<string>();
  if (Date.now() - universe.at > UNIVERSE_EVERY_MS) {
    try {
      universe = { at: Date.now(), ids: (await index(UNIVERSE_INDEX)).components.map((c) => c.id) };
    } catch (e) {
      log(`ת"א-35 components: ${e instanceof Error ? e.message : e}`); // keep the last known set
    }
  }
  for (const id of universe.ids) others.add(id);
  for (const item of (await readList()).items) if (item.kind === 'security') others.add(item.id);
  const a = await account();
  if (a) for (const p of positions(a.trades).positions) others.add(p.paper.id);
  for (const id of strategy) others.delete(id);
  return { strategy, others: [...others] };
}

const status: TraderStatus = { at: '', pid: process.pid, marketOpen: false, enabled: false, papers: {} };

async function loop() {
  const now = new Date();
  const cfg = await config();
  const open = await isOpen();
  status.at = now.toISOString();
  status.marketOpen = open;
  status.enabled = cfg.enabled;
  const { strategy, others } = await papersToRecord(cfg);
  if (open) closedPass.clear();
  const due = (id: string) => {
    if (!open) return !closedPass.has(id);
    if (strategy.includes(id)) return true;
    return now.getTime() - (lastRecorded.get(id) ?? 0) >= OTHERS_EVERY_MS;
  };
  const today = israelDate(now);
  const day = open && strategy.length > 0 ? await readJournal(today) : null;

  await Promise.all(
    [...strategy, ...others].filter(due).map(async (id) => {
      const s = (status.papers[id] ??= { recordedAt: null, trades: 0, tradeDate: null, error: null });
      try {
        const { tradeDate, ticks } = await dayTrades(id, 0);
        lastRecorded.set(id, now.getTime());
        if (!open) closedPass.add(id);
        if (tradeDate && ticks.length > 0) await recordDay(id, tradeDate, ticks);
        Object.assign(s, { recordedAt: now.toISOString(), trades: ticks.length, tradeDate, error: null });
        // Only on today's trades, only while the session runs.
        if (day && strategy.includes(id) && tradeDate === today) await runStrategy(id, ticks, today, now, cfg, day);
      } catch (e) {
        s.error = e instanceof Error ? e.message : String(e);
        log(`[${id}] ${s.error}`);
      }
    }),
  );
  await writeJson('trader-status.json', status);
}

async function main() {
  log(`bursa trader up (pid ${process.pid})`);
  for (const sig of ['SIGTERM', 'SIGINT'] as const) process.on(sig, () => (stopping = true));
  while (!stopping) {
    const started = Date.now();
    try {
      await loop();
    } catch (e) {
      log('loop failed:', e instanceof Error ? e.stack : e);
    }
    // Sleep in short steps so a stop request is answered within a second.
    while (!stopping && Date.now() - started < LOOP_MS) await new Promise((r) => setTimeout(r, 500));
  }
  log('bursa trader stopped');
}

await main();
