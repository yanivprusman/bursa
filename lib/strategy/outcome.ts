// A replayed day in money — the same sizing and fees as the trader and the account.

import { feeOf, grossOf } from '../account.ts';
import type { Params, Signal } from './emaVwap.ts';

/**
 * The replay in money: each round trip sized by the budget, at the bar's close, with the
 * account's own fees. A position the record leaves open is valued at the last close.
 */
export function outcome(signals: Signal[], lastClose: number | null, p: Params) {
  let pnl = 0;
  let trips = 0;
  let wins = 0;
  let entry: { price: number; qty: number } | null = null;
  const close = (price: number) => {
    if (!entry) return;
    const net = grossOf(entry.qty, price) - feeOf(grossOf(entry.qty, price)) - grossOf(entry.qty, entry.price) - feeOf(grossOf(entry.qty, entry.price));
    pnl += net;
    trips++;
    if (net > 0) wins++;
    entry = null;
  };
  for (const s of signals) {
    if (s.side === 'buy') entry = { price: s.price, qty: Math.floor(p.budget / s.price) };
    else close(s.price);
  }
  const open = entry !== null;
  if (open && lastClose !== null) close(lastClose);
  return { trips, wins, open, pnl, pct: (pnl / p.budget) * 100 };
}
