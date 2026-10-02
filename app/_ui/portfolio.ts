// What a holding is worth. The exchange quotes every traded security in agorot, so
// shekels = quantity × price / 100 — for a share, an ETF unit and a bond's par value
// alike. The same arithmetic as the phone's PortfolioMath.kt, with the same test cases.

export type HoldingValue = {
  /** Shekels, at the last price. */
  value: number;
  /** Shekels gained or lost since the previous close; null when there is no previous close. */
  dayChange: number | null;
  /** Shekels paid; null when no purchase price was entered. */
  cost: number | null;
  gain: number | null;
  gainPct: number | null;
};

export function holdingValue(qty: number, avgCost: number | null, last: number, base: number | null): HoldingValue {
  const value = (qty * last) / 100;
  const cost = avgCost === null ? null : (qty * avgCost) / 100;
  const gain = cost === null ? null : value - cost;
  return {
    value,
    dayChange: base === null ? null : (qty * (last - base)) / 100,
    cost,
    gain,
    gainPct: gain !== null && cost !== null && cost > 0 ? (gain / cost) * 100 : null,
  };
}

export type Totals = {
  value: number;
  dayChange: number;
  /** Against what the same holdings were worth at the previous close. */
  dayChangePct: number | null;
  /** Summed only over holdings that have a purchase price. */
  gain: number | null;
  gainPct: number | null;
  /** True when some holding has no purchase price, so `gain` does not cover the whole portfolio. */
  gainIsPartial: boolean;
};

export function totals(holdings: HoldingValue[]): Totals {
  const value = holdings.reduce((s, h) => s + h.value, 0);
  const dayChange = holdings.reduce((s, h) => s + (h.dayChange ?? 0), 0);
  const previous = value - dayChange;
  const costed = holdings.filter((h) => h.cost !== null);
  const cost = costed.reduce((s, h) => s + (h.cost ?? 0), 0);
  const gain = costed.length ? costed.reduce((s, h) => s + (h.gain ?? 0), 0) : null;
  return {
    value,
    dayChange,
    dayChangePct: previous > 0 ? (dayChange / previous) * 100 : null,
    gain,
    gainPct: gain !== null && cost > 0 ? (gain / cost) * 100 : null,
    gainIsPartial: costed.length > 0 && costed.length < holdings.length,
  };
}
