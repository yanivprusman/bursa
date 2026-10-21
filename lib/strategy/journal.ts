// The trader's journal: every decision it made, per day (strategy/<date>.json). Written
// only by the trader — each decision BEFORE its order is sent — and read by the web
// server to show what it did and why.

import { readText, writeJson } from '../store.ts';
import type { Reason } from './emaVwap.ts';

export type Event = {
  at: string;
  /** The bar ("HH:MM") whose close made the decision. */
  bar: string;
  side: 'buy' | 'sell';
  reason: Reason;
  qty: number;
  signal: string;
  orderId: string | null;
  /** What it filled at, agorot; null when it did not fill. */
  price: number | null;
  error: string | null;
};

export type Day = { date: string; papers: Record<string, { lastBar: string | null; events: Event[] }> };

const file = (date: string) => `strategy/${date}.json`;

export async function readJournal(date: string): Promise<Day> {
  const text = await readText(file(date));
  return text === null ? { date, papers: {} } : (JSON.parse(text) as Day);
}

export const writeJournal = (day: Day) => writeJson(file(day.date), day);

/** The trader's heartbeat (trader-status.json), rewritten every round. */
export type TraderStatus = {
  at: string;
  pid: number;
  marketOpen: boolean;
  enabled: boolean;
  papers: Record<string, { recordedAt: string | null; trades: number; tradeDate: string | null; error: string | null }>;
};

export async function readStatus(): Promise<TraderStatus | null> {
  const text = await readText('trader-status.json');
  return text === null ? null : (JSON.parse(text) as TraderStatus);
}
