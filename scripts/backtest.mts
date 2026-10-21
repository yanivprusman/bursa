// Replay the strategy over every recorded day (lib/ticks.ts) and print what it would
// have made. The same bars, rules, sizing and fees as the live trader.
//
//   node scripts/backtest.mts                        # every recorded paper, default rules
//   node scripts/backtest.mts 629014,1081124         # some papers
//   node scripts/backtest.mts all stopPct=1 budget=2000000
//
// Reads the dev recording by default; NODE_ENV=production reads prod's.

import { readdir } from 'node:fs/promises';
import { dataFile } from '../lib/store.ts';
import { readDay, recordedDays } from '../lib/ticks.ts';
import { bars } from '../lib/strategy/bars.ts';
import { params, replay } from '../lib/strategy/emaVwap.ts';
import { outcome } from '../lib/strategy/outcome.ts';

process.env.AUTOMATE_LINUX_DIR ??= '/opt/automateLinux';
const [which = 'all', ...overrides] = process.argv.slice(2);
const p = params(Object.fromEntries(overrides.map((kv) => {
  const [k, v] = kv.split('=');
  return [k, /^\d{2}:\d{2}$/.test(v) ? v : Number(v)];
})));

async function allPapers(): Promise<string[]> {
  const ids = new Set<string>();
  for (const d of await readdir(dataFile('ticks')).catch(() => [] as string[])) {
    for (const f of await readdir(dataFile(`ticks/${d}`)).catch(() => [] as string[])) ids.add(f.replace(/\.json$/, ''));
  }
  return [...ids].sort();
}

const papers = which === 'all' ? await allPapers() : which.split(',');
let total = 0;
let trips = 0;
let wins = 0;
let days = 0;
for (const id of papers) {
  for (const date of await recordedDays(id)) {
    const b = bars(await readDay(id, date), p.barMinutes, p.emaPeriod);
    if (b.length === 0) continue;
    const o = outcome(replay(b, date, p), b.at(-1)!.c, p);
    days++;
    total += o.pnl;
    trips += o.trips;
    wins += o.wins;
    if (o.trips > 0) console.log(`${date}  ${id.padStart(9)}  ${String(o.trips).padStart(2)} trips  ${(o.pnl / 100).toFixed(2).padStart(10)} ₪  ${o.pct.toFixed(2).padStart(6)}%`);
  }
}
console.log(`\n${papers.length} papers, ${days} paper-days, ${trips} round trips, ${wins} won — total ${(total / 100).toFixed(2)} ₪ on ₪${p.budget / 100} per trade`);
