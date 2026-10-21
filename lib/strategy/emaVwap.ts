// The "EMA-9 crosses VWAP" strategy, long only, on 3-minute bars.
//
// From a social-media clip (roboquant): buy when the 9-bar EMA crosses up through the
// session VWAP; get out when a bar closes back below the EMA, or at a stop. The clip
// trades call options with a 25–30% stop; this account holds shares, so the stop is a
// share-price stop and much tighter. The account has no shorting, so the cross down
// (puts, in the clip) is not traded. VWAP is a one-session line, so the strategy is out
// of the market before every close.
//
// Everything is decided on a COMPLETED bar only, never mid-bar: the live trader and the
// replay of a recorded day make the same decision from the same bars.

import type { Bar } from './bars.ts';
import { minuteOf } from './bars.ts';

export const STRATEGY = 'ema9-vwap';

export type Params = {
  barMinutes: number;
  emaPeriod: number;
  /** No entry before this many bars exist — the EMA needs them to mean anything. */
  minBars: number;
  /** Sell when a bar closes this many percent under the entry price. */
  stopPct: number;
  /** Agorot to put into one entry. */
  budget: number;
  /** No new entries from this time ("HH:MM"); out of everything at `flatAt`. Mon–Thu and Friday differ. */
  lastEntry: string;
  flatAt: string;
  fridayLastEntry: string;
  fridayFlatAt: string;
};

export const DEFAULTS: Params = {
  barMinutes: 3,
  emaPeriod: 9,
  minBars: 9,
  stopPct: 1.5,
  budget: 1_000_000, // ₪10,000
  lastEntry: '16:30',
  flatAt: '17:00',
  fridayLastEntry: '13:00',
  fridayFlatAt: '13:30',
};

/** A user-chosen override, validated; anything not given keeps its default. */
export function params(raw: unknown): Params {
  const o = (raw ?? {}) as Record<string, unknown>;
  const out = { ...DEFAULTS };
  const int = (k: keyof Params, lo: number, hi: number) => {
    if (o[k] === undefined) return;
    const v = o[k];
    if (typeof v !== 'number' || !Number.isInteger(v) || v < lo || v > hi) throw new Error(`${k} must be an integer in ${lo}..${hi}`);
    (out[k] as number) = v;
  };
  const time = (k: keyof Params) => {
    if (o[k] === undefined) return;
    if (typeof o[k] !== 'string' || !/^\d{2}:\d{2}$/.test(o[k] as string)) throw new Error(`${k} must be HH:MM`);
    (out[k] as string) = o[k] as string;
  };
  int('barMinutes', 1, 60);
  int('emaPeriod', 2, 100);
  int('minBars', 1, 200);
  int('budget', 10_000, 100_000_000);
  if (o.stopPct !== undefined) {
    if (typeof o.stopPct !== 'number' || !(o.stopPct > 0 && o.stopPct < 50)) throw new Error('stopPct must be between 0 and 50');
    out.stopPct = o.stopPct;
  }
  for (const k of ['lastEntry', 'flatAt', 'fridayLastEntry', 'fridayFlatAt'] as const) time(k);
  return out;
}

/** The day's entry cut-off and flat time. `date` is the Israel date, YYYY-MM-DD. */
export function session(date: string, p: Params) {
  const friday = new Date(`${date}T12:00:00Z`).getUTCDay() === 5;
  return friday ? { lastEntry: p.fridayLastEntry, flatAt: p.fridayFlatAt } : { lastEntry: p.lastEntry, flatAt: p.flatAt };
}

export type Reason = 'cross-up' | 'stop' | 'below-ema' | 'end-of-day';
export type Decision = { side: 'buy' | 'sell'; reason: Reason } | null;

/** What the strategy holds: the entry price (agorot) and the day it was bought. */
export type Holding = { entry: number; date: string };

/**
 * What to do at the close of `bars[i]`, holding `pos` or nothing. `bars` are the day's
 * completed bars, oldest first. A holding from an earlier day (the trader was down at
 * the close) is sold at the first bar: it was meant to be out by then.
 */
export function decide(bars: Bar[], i: number, pos: Holding | null, date: string, p: Params): Decision {
  const cur = bars[i];
  const prev = i > 0 ? bars[i - 1] : null;
  const s = session(date, p);
  if (pos) {
    if (pos.date < date) return { side: 'sell', reason: 'end-of-day' };
    if (cur.c <= pos.entry * (1 - p.stopPct / 100)) return { side: 'sell', reason: 'stop' };
    if (cur.c < cur.ema) return { side: 'sell', reason: 'below-ema' };
    if (minuteOf(cur.end) >= minuteOf(s.flatAt)) return { side: 'sell', reason: 'end-of-day' };
    return null;
  }
  if (i + 1 < p.minBars || !prev || prev.vwap === null || cur.vwap === null) return null;
  if (minuteOf(cur.end) > minuteOf(s.lastEntry)) return null;
  const crossedUp = prev.ema <= prev.vwap && cur.ema > cur.vwap;
  // A cross on a bar that already closed under its EMA would be sold on the next bar.
  return crossedUp && cur.c >= cur.ema ? { side: 'buy', reason: 'cross-up' } : null;
}

export type Signal = { t: string; side: 'buy' | 'sell'; reason: Reason; price: number };

/**
 * The day replayed: every decision, filling at the bar's close. `flat` closes a position
 * still open after the last bar (the record ended before `flatAt`) at that bar's close.
 */
export function replay(bars: Bar[], date: string, p: Params): Signal[] {
  const out: Signal[] = [];
  let pos: Holding | null = null;
  for (let i = 0; i < bars.length; i++) {
    const d = decide(bars, i, pos, date, p);
    if (!d) continue;
    out.push({ t: bars[i].t, side: d.side, reason: d.reason, price: bars[i].c });
    pos = d.side === 'buy' ? { entry: bars[i].c, date } : null;
  }
  return out;
}
