// What the owner asked the trader to do: on or off, on which papers, with which numbers.
// One JSON file (strategy.json), written only by the web server (/api/strategy), read by
// the trader on every round — so a switch takes effect within one round, ~20 s.

import { StoreError, readText, serial, writeJson } from '../store.ts';
import { params, type Params } from './emaVwap.ts';

export type Config = { rev: number; enabled: boolean; papers: string[]; params: Params };

const FILE = 'strategy.json';
const ID = /^\d{1,9}$/;
const MAX_PAPERS = 10;

export async function readConfig(): Promise<Config> {
  const text = await readText(FILE);
  if (text === null) return { rev: 0, enabled: false, papers: [], params: params({}) };
  try {
    const raw = JSON.parse(text) as Partial<Config>;
    if (typeof raw.rev !== 'number' || !Array.isArray(raw.papers)) throw new Error('wrong shape');
    return { rev: raw.rev, enabled: raw.enabled === true, papers: raw.papers.filter((x) => typeof x === 'string' && ID.test(x)), params: params(raw.params) };
  } catch (e) {
    throw new StoreError(`${FILE} is damaged (${(e as Error).message}); not touching it`, 500);
  }
}

const inTurn = serial();

/** `{op:"enable", on}` — the switch for all of it; `{op:"paper", id, on}`; `{op:"params", params}`. */
export function changeConfig(op: unknown): Promise<Config> {
  return inTurn(async () => {
    const o = (op ?? {}) as Record<string, unknown>;
    const c = await readConfig();
    let next: Config;
    switch (o.op) {
      case 'enable':
        if (typeof o.on !== 'boolean') throw new StoreError('on חייב להיות true או false');
        next = { ...c, enabled: o.on };
        break;
      case 'paper': {
        if (typeof o.id !== 'string' || !ID.test(o.id) || typeof o.on !== 'boolean') throw new StoreError('נדרשים id ו-on');
        const papers = c.papers.filter((x) => x !== o.id);
        if (o.on) papers.push(o.id);
        if (papers.length > MAX_PAPERS) throw new StoreError(`האסטרטגיה רצה על ${MAX_PAPERS} ניירות לכל היותר`, 409);
        next = { ...c, papers };
        break;
      }
      case 'params':
        try {
          next = { ...c, params: params({ ...c.params, ...(o.params as object) }) };
        } catch (e) {
          throw new StoreError((e as Error).message);
        }
        break;
      default:
        throw new StoreError('פעולה לא מוכרת');
    }
    next = { ...next, rev: c.rev + 1 };
    await writeJson(FILE, next);
    return next;
  });
}
