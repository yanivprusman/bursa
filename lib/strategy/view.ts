// What the strategy panel shows for one paper on one day: the bars it decides on, what
// it would have done (the replay), what it actually did (the journal and the account's
// tagged trades), and whether the trader is alive.

import { israelClock, israelDate, readAccount } from '../account.ts';
import { dayTrades, tradeQuote } from '../market.ts';
import { StoreError } from '../store.ts';
import { readDay, recordedDays } from '../ticks.ts';
import { bars, closedBars } from './bars.ts';
import { readConfig } from './config.ts';
import { STRATEGY, replay, session } from './emaVwap.ts';
import { outcome } from './outcome.ts';
import { readJournal, readStatus } from './journal.ts';

/** The trader writes its heartbeat every ~20 s; past this it is not running. */
const ALIVE_MS = 90_000;
const GRACE_SEC = 20;

export async function strategyView(id: string, date: string | null, now = new Date()) {
  if (!/^\d{1,9}$/.test(id)) throw new StoreError('מזהה נייר לא תקין');
  const today = israelDate(now);
  const day = date ?? today;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) throw new StoreError('תאריך לא תקין');

  const [cfg, status, journal, account, recorded] = await Promise.all([readConfig(), readStatus(), readJournal(day), readAccount(tradeQuote), recordedDays(id)]);
  const p = cfg.params;

  // Today: the exchange's own feed (what the trader reads). Another day: the recording.
  let ticks = await readDay(id, day);
  let isToday = false;
  if (day === today) {
    const live = await dayTrades(id);
    if (live.tradeDate === today) {
      ticks = live.ticks;
      isToday = true;
    }
  }
  const all = bars(ticks, p.barMinutes, p.emaPeriod);
  // On a finished day every bar is complete; today the last one may still be forming.
  const done = isToday ? closedBars(all, israelClock(now), GRACE_SEC) : all;

  const signals = replay(done, day, p);
  const fresh = status !== null && now.getTime() - Date.parse(status.at) < ALIVE_MS;
  return {
    strategy: STRATEGY,
    date: day,
    today,
    /** Recorded days, and always today — there is always a today to go back to. */
    days: [...new Set([...recorded, today])].sort(),
    params: p,
    session: session(day, p),
    enabled: cfg.enabled,
    following: cfg.papers.includes(id),
    papers: cfg.papers,
    trader: { alive: fresh, at: status?.at ?? null, marketOpen: status?.marketOpen ?? false },
    bars: all,
    /** How many of `bars` are complete; a forming bar is drawn but never decided on. */
    closed: done.length,
    replay: signals,
    outcome: outcome(signals, done.at(-1)?.c ?? null, p),
    events: journal.papers[id]?.events ?? [],
    lastDecided: journal.papers[id]?.lastBar ?? null,
    trades: account.trades.filter((t) => t.paper.id === id && t.by?.strategy === STRATEGY && (t.tradeDate ?? t.at.slice(0, 10)) === day),
  };
}

export type StrategyView = Awaited<ReturnType<typeof strategyView>>;
