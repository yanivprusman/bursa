// The owner's list: what is followed, and what is held. One list, shared by the
// phone and the desktop, kept in one JSON file on this machine.
//
// Every change is an OPERATION ("follow this", "hold 50 of that") applied here, not
// a whole list sent from a client — so the phone and the desktop can both be open
// and neither can overwrite what the other just did.

import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';

export type Kind = 'security' | 'index';

export type Tracked = {
  kind: Kind;
  id: string;
  name: string;
  symbol: string | null;
  type: string | null;
  companyId: string | null;
  /** Units held; null when the paper is only followed. For a bond, par value in shekels. */
  qty: number | null;
  /** Average purchase price in agorot; null when it was not entered. */
  avgCost: number | null;
};

export type List = { rev: number; updatedAt: string | null; items: Tracked[] };

export class ListError extends Error {
  readonly status: number;
  // Spelled out rather than a parameter property, so `node --test` can run this file as it is.
  constructor(message: string, status = 400) {
    super(message);
    this.name = 'ListError';
    this.status = status;
  }
}

/** Dev and prod keep separate lists, so an experiment in dev cannot touch the real one. */
function dataDir(): string {
  if (process.env.BURSA_DATA_DIR) return process.env.BURSA_DATA_DIR;
  const root = process.env.AUTOMATE_LINUX_DIR;
  if (!root) throw new ListError('AUTOMATE_LINUX_DIR is not set, so there is nowhere to keep the list', 500);
  return path.join(root, 'data', 'bursa', process.env.NODE_ENV === 'production' ? 'prod' : 'dev');
}

const file = () => path.join(dataDir(), 'list.json');

async function load(): Promise<List> {
  let text: string;
  try {
    text = await readFile(file(), 'utf8');
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === 'ENOENT') return { rev: 0, updatedAt: null, items: [] };
    throw e;
  }
  // A file that is there but unreadable is never treated as "empty": that would let the
  // next change overwrite the owner's holdings with a list of one.
  try {
    const parsed = JSON.parse(text) as List;
    if (!Array.isArray(parsed.items) || typeof parsed.rev !== 'number') throw new Error('wrong shape');
    return parsed;
  } catch (e) {
    throw new ListError(`${file()} is damaged (${(e as Error).message}); not touching it`, 500);
  }
}

async function save(list: List): Promise<void> {
  await mkdir(dataDir(), { recursive: true });
  const tmp = `${file()}.${process.pid}.tmp`;
  await writeFile(tmp, JSON.stringify(list, null, 2) + '\n', { mode: 0o600 });
  await rename(tmp, file());
}

// One change at a time: two requests must not both read revision 7 and both write 8.
let queue: Promise<unknown> = Promise.resolve();
function inTurn<T>(fn: () => Promise<T>): Promise<T> {
  const run = queue.then(fn, fn);
  queue = run.catch(() => undefined);
  return run;
}

export function readList(): Promise<List> {
  return inTurn(load);
}

// ── validating what a client sends ──────────────────────────────────────────

function text(v: unknown, max: number): string | null {
  if (v === null || v === undefined || v === '') return null;
  if (typeof v !== 'string' || v.length > max) throw new ListError('שדה טקסט לא תקין');
  return v;
}

function ref(v: unknown): { kind: Kind; id: string } {
  const o = (v ?? {}) as Record<string, unknown>;
  if (o.kind !== 'security' && o.kind !== 'index') throw new ListError('kind חייב להיות security או index');
  if (typeof o.id !== 'string' || !/^\d{1,9}$/.test(o.id)) throw new ListError('מזהה נייר לא תקין');
  return { kind: o.kind, id: o.id };
}

function item(v: unknown): Tracked {
  const o = (v ?? {}) as Record<string, unknown>;
  const name = text(o.name, 120);
  if (!name) throw new ListError('חסר שם לנייר');
  const companyId = text(o.companyId, 9);
  if (companyId !== null && !/^\d{1,9}$/.test(companyId)) throw new ListError('מספר חברה לא תקין');
  return { ...ref(o), name, symbol: text(o.symbol, 40), type: text(o.type, 60), companyId, qty: null, avgCost: null };
}

function positive(v: unknown, what: string): number {
  if (typeof v !== 'number' || !Number.isFinite(v) || v <= 0 || v > 1e12) throw new ListError(`${what} חייב להיות מספר גדול מאפס`);
  return v;
}

function holding(o: Record<string, unknown>): { qty: number; avgCost: number | null } {
  return {
    qty: positive(o.qty, 'כמות'),
    avgCost: o.avgCost === null || o.avgCost === undefined ? null : positive(o.avgCost, 'מחיר קנייה'),
  };
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
      if (items.length >= MAX_ITEMS) throw new ListError(`הרשימה מלאה (${MAX_ITEMS} ניירות)`);
      return [...items, it];
    }
    case 'unfollow': {
      const r = ref(o);
      return items.filter((x) => !same(x, r));
    }
    case 'hold': {
      const it = item(o.item);
      if (it.kind === 'index') throw new ListError('אי אפשר להחזיק מדד');
      const held = { ...it, ...holding(o) };
      const at = items.findIndex((x) => same(x, it));
      if (at >= 0) return items.map((x, i) => (i === at ? { ...x, qty: held.qty, avgCost: held.avgCost } : x));
      if (items.length >= MAX_ITEMS) throw new ListError(`הרשימה מלאה (${MAX_ITEMS} ניירות)`);
      return [...items, held];
    }
    case 'clearHolding': {
      const r = ref(o);
      return items.map((x) => (same(x, r) ? { ...x, qty: null, avgCost: null } : x));
    }
    case 'import': {
      // A phone that kept its list locally (before the list moved here) hands it over once.
      // Only papers this list does not have yet are taken; nothing already here changes.
      if (!Array.isArray(o.items)) throw new ListError('חסרה רשימה לייבוא');
      let out = items;
      for (const raw of o.items) {
        const r = (raw ?? {}) as Record<string, unknown>;
        const it = item(r);
        if (out.some((x) => same(x, it))) continue;
        if (out.length >= MAX_ITEMS) throw new ListError(`הרשימה מלאה (${MAX_ITEMS} ניירות)`);
        const held = it.kind === 'security' && r.qty !== null && r.qty !== undefined ? holding(r) : null;
        out = [...out, held ? { ...it, ...held } : it];
      }
      return out;
    }
    default:
      throw new ListError('פעולה לא מוכרת');
  }
}

export function change(op: unknown): Promise<List> {
  return inTurn(async () => {
    const list = await load();
    const items = apply(list.items, op);
    if (items === list.items) return list;
    const next = { rev: list.rev + 1, updatedAt: new Date().toISOString(), items };
    await save(next);
    return next;
  });
}
