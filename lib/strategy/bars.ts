// Trades → candles with VWAP and an EMA on them. Pure: the same function draws the chart,
// drives the trader and replays a recorded day, so all three always see the same bars.

import type { Tick } from '../market';

export type Bar = {
  /** "HH:MM", Israel time: the minute the bar starts. */
  t: string;
  /** "HH:MM": the minute it ends (exclusive) — the bar is complete once the clock passes it. */
  end: string;
  o: number;
  h: number;
  l: number;
  c: number;
  /** Turnover inside the bar (the exchange's unit). */
  v: number;
  /** The session's VWAP at the bar's close; null until anything has traded with turnover. */
  vwap: number | null;
  /** EMA of the closes, seeded with the first close. */
  ema: number;
};

export const minuteOf = (hhmm: string) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));
export const hhmm = (minute: number) => `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`;

/**
 * Bars of `minutes` minutes, aligned to the clock (a 3-minute bar starts at :00, :03, …).
 * A stretch with no trades makes no bar — the EMA runs over the bars that exist. The
 * exchange stamps trades to the minute, so a trade belongs to the bar holding its minute;
 * within a minute the exchange's trade number gives the order.
 */
export function bars(ticks: Tick[], minutes: number, emaPeriod: number): Bar[] {
  const k = 2 / (emaPeriod + 1);
  const out: Bar[] = [];
  let pv = 0; // Σ turnover
  let q = 0; // Σ turnover / price — i.e. Σ quantity, in the same unit
  let ema: number | null = null;
  let cur: Bar | null = null;
  const close = () => {
    if (!cur) return;
    ema = ema === null ? cur.c : cur.c * k + ema * (1 - k);
    cur.ema = ema;
    cur.vwap = q > 0 ? pv / q : null;
    out.push(cur);
  };
  for (const tick of [...ticks].sort((a, b) => a.id - b.id)) {
    const start = Math.floor(minuteOf(tick.t) / minutes) * minutes;
    const t = hhmm(start);
    if (!cur || cur.t !== t) {
      // Ticks arrive in trade-number order; a minute stamp that runs backwards (it has
      // not been seen) would otherwise reopen an earlier bar — it joins the current one.
      if (cur && minuteOf(t) < minuteOf(cur.t)) {
        /* same bar */
      } else {
        close();
        cur = { t, end: hhmm(start + minutes), o: tick.p, h: tick.p, l: tick.p, c: tick.p, v: 0, vwap: null, ema: 0 };
      }
    }
    cur!.h = Math.max(cur!.h, tick.p);
    cur!.l = Math.min(cur!.l, tick.p);
    cur!.c = tick.p;
    cur!.v += tick.v;
    if (tick.v > 0) {
      pv += tick.v;
      q += tick.v / tick.p;
    }
  }
  close();
  return out;
}

/** The bars that are over by `clock` ("HH:MM:SS", Israel time), allowing `graceSec` for the feed to catch up. */
export function closedBars(all: Bar[], clock: string, graceSec: number): Bar[] {
  const [h, m, s] = clock.split(':').map(Number);
  const now = h * 3600 + m * 60 + (s || 0);
  return all.filter((b) => minuteOf(b.end) * 60 + graceSec <= now);
}
