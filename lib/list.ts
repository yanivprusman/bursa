// The owner's watchlist: what is followed. One list, shared by the phone and the
// desktop, kept in one JSON file on this machine. (What is held is the trading
// account's business — lib/account.ts — and only a trade changes it.)
//
// Every change is an OPERATION ("follow this", "unfollow that") applied here, not
// a whole list sent from a client — so the phone and the desktop can both be open
// and neither can overwrite what the other just did.

import { StoreError, readText, serial, writeJson } from './store.ts';

export { StoreError as ListError };

export type Kind = 'security' | 'index';

/** A paper the owner follows. What is HELD is not here: holdings come only from trades (lib/account.ts). */
export type Tracked = {
  kind: Kind;
  id: string;
  name: string;
  symbol: string | null;
  type: string | null;
  companyId: string | null;
};

export type List = { rev: number; updatedAt: string | null; items: Tracked[] };

const FILE = 'list.json';

async function load(): Promise<List> {
  const text = await readText(FILE);
  if (text === null) return { rev: 0, updatedAt: null, items: [] };
  // A file that is there but unreadable is never treated as "empty": that would let the
  // next change overwrite the owner's list with a list of one.
  try {
    const parsed = JSON.parse(text) as List;
    if (!Array.isArray(parsed.items) || typeof parsed.rev !== 'number') throw new Error('wrong shape');
    // Lists written before holdings moved to trades carried typed-in quantities; those are not shown.
    return { ...parsed, items: parsed.items.map(({ kind, id, name, symbol, type, companyId }) => ({ kind, id, name, symbol, type, companyId })) };
  } catch (e) {
    throw new StoreError(`${FILE} is damaged (${(e as Error).message}); not touching it`, 500);
  }
}

const inTurn = serial();

export function readList(): Promise<List> {
  return inTurn(load);
}

// ── validating what a client sends ──────────────────────────────────────────

function text(v: unknown, max: number): string | null {
  if (v === null || v === undefined || v === '') return null;
  if (typeof v !== 'string' || v.length > max) throw new StoreError('שדה טקסט לא תקין');
  return v;
}

function ref(v: unknown): { kind: Kind; id: string } {
  const o = (v ?? {}) as Record<string, unknown>;
  if (o.kind !== 'security' && o.kind !== 'index') throw new StoreError('kind חייב להיות security או index');
  if (typeof o.id !== 'string' || !/^\d{1,9}$/.test(o.id)) throw new StoreError('מזהה נייר לא תקין');
  return { kind: o.kind, id: o.id };
}

function item(v: unknown): Tracked {
  const o = (v ?? {}) as Record<string, unknown>;
  const name = text(o.name, 120);
  if (!name) throw new StoreError('חסר שם לנייר');
  const companyId = text(o.companyId, 9);
  if (companyId !== null && !/^\d{1,9}$/.test(companyId)) throw new StoreError('מספר חברה לא תקין');
  return { ...ref(o), name, symbol: text(o.symbol, 40), type: text(o.type, 60), companyId };
}

const MAX_ITEMS = 80; // what /api/quotes will price in one request

const same = (a: { kind: Kind; id: string }, b: { kind: Kind; id: string }) => a.kind === b.kind && a.id === b.id;

/** Apply one operation to a list. Pure: the caller decides whether anything changed. */
export function apply(items: Tracked[], op: unknown): Tracked[] {
  const o = (op ?? {}) as Record<string, unknown>;
  switch (o.op) {
    case 'follow': {
      const it = item(o.item);
      if (items.some((x) => same(x, it))) return items;
      if (items.length >= MAX_ITEMS) throw new StoreError(`הרשימה מלאה (${MAX_ITEMS} ניירות)`);
      return [...items, it];
    }
    case 'unfollow': {
      const r = ref(o);
      return items.filter((x) => !same(x, r));
    }
    case 'hold':
    case 'clearHolding':
      // An app from before trading still offers to type a holding in. It no longer exists.
      throw new StoreError('החזקה נוצרת רק מקנייה במסחר — יש לעדכן את האפליקציה', 410);
    case 'import': {
      // A phone that kept its list locally (before the list moved here) hands it over once.
      // Only papers this list does not have yet are taken; nothing already here changes.
      if (!Array.isArray(o.items)) throw new StoreError('חסרה רשימה לייבוא');
      let out = items;
      for (const raw of o.items) {
        // A typed-in quantity that comes along is ignored: holdings come only from trades.
        const it = item(raw);
        if (out.some((x) => same(x, it))) continue;
        if (out.length >= MAX_ITEMS) throw new StoreError(`הרשימה מלאה (${MAX_ITEMS} ניירות)`);
        out = [...out, it];
      }
      return out;
    }
    default:
      throw new StoreError('פעולה לא מוכרת');
  }
}

export function change(op: unknown): Promise<List> {
  return inTurn(async () => {
    const list = await load();
    const items = apply(list.items, op);
    if (items === list.items) return list;
    const next = { rev: list.rev + 1, updatedAt: new Date().toISOString(), items };
    await writeJson(FILE, next);
    return next;
  });
}
