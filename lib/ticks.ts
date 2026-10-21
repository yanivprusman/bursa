// The recorded trades: every trade of every day the trader saw, one file per paper per day.
//
// The exchange's intraday feed holds only today and is cleared before the next opening,
// so this record is the only minute-level history there is — what the strategy is tested
// against (lib/strategy/). Only the trader (scripts/trader.mts) writes it; recording the
// same day again merges by the exchange's own trade number, so nothing is counted twice
// and a trade seen once is never lost to a later, shorter answer.

import { readdir } from 'node:fs/promises';
import type { Tick } from './market';
import { StoreError, dataFile, readText, writeJson } from './store.ts';

const name = (date: string, id: string) => `ticks/${date}/${id}.json`;

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const ID = /^\d{1,9}$/;

function check(date: string, id: string) {
  if (!DATE.test(date) || !ID.test(id)) throw new StoreError('תאריך או מזהה נייר לא תקינים');
}

export async function readDay(id: string, date: string): Promise<Tick[]> {
  check(date, id);
  const text = await readText(name(date, id));
  if (text === null) return [];
  try {
    const ticks = JSON.parse(text) as Tick[];
    if (!Array.isArray(ticks)) throw new Error('wrong shape');
    return ticks;
  } catch (e) {
    throw new StoreError(`${name(date, id)} is damaged (${(e as Error).message})`, 500);
  }
}

/** Merge what the feed holds now into the day's file. Returns how many trades were new. */
export async function recordDay(id: string, date: string, ticks: Tick[]): Promise<number> {
  check(date, id);
  const had = await readDay(id, date);
  const byId = new Map(had.map((t) => [t.id, t]));
  let added = 0;
  for (const t of ticks) {
    if (byId.has(t.id)) continue;
    byId.set(t.id, t);
    added++;
  }
  if (added > 0) await writeJson(name(date, id), [...byId.values()].sort((a, b) => a.id - b.id));
  return added;
}

/** The days recorded for a paper, oldest first. */
export async function recordedDays(id: string): Promise<string[]> {
  if (!ID.test(id)) throw new StoreError('מזהה נייר לא תקין');
  let days: string[];
  try {
    days = await readdir(dataFile('ticks'));
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === 'ENOENT') return [];
    throw e;
  }
  const out: string[] = [];
  for (const d of days.filter((d) => DATE.test(d)).sort()) {
    if ((await readText(name(d, id))) !== null) out.push(d);
  }
  return out;
}
