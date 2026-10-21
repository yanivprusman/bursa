import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

process.env.BURSA_DATA_DIR = mkdtempSync(path.join(tmpdir(), 'bursa-strategy-'));
const { bars, closedBars } = await import('../lib/strategy/bars.ts');
const S = await import('../lib/strategy/emaVwap.ts');
const A = await import('../lib/account.ts');
const T = await import('../lib/ticks.ts');
const { withFileLock, dataFile } = await import('../lib/store.ts');
type Tick = import('../lib/market.ts').Tick;
type TradeQuote = import('../lib/market.ts').TradeQuote;

let n = 0;
const tick = (t: string, p: number, v = 100): Tick => ({ id: ++n, t, p, v });

test('bars: clock-aligned 3-minute candles, OHLC and turnover', () => {
  const b = bars([tick('10:00', 100), tick('10:01', 105), tick('10:02', 98), tick('10:03', 101)], 3, 9);
  assert.equal(b.length, 2);
  assert.deepEqual([b[0].t, b[0].end, b[0].o, b[0].h, b[0].l, b[0].c, b[0].v], ['10:00', '10:03', 100, 105, 98, 98, 300]);
  assert.equal(b[1].t, '10:03');
});

test('VWAP weights by quantity (turnover / price) and ignores zero-turnover trades', () => {
  // ₪1000 at 100 (10 units) + ₪1000 at 200 (5 units) → 2000 / 15
  const b = bars([tick('10:00', 100, 1000), tick('10:00', 200, 1000), tick('10:01', 999, 0)], 3, 9);
  assert.ok(Math.abs(b[0].vwap! - 2000 / 15) < 1e-9);
  assert.equal(b[0].c, 999, 'a zero-turnover trade still sets the price');
  assert.equal(bars([tick('10:00', 100, 0)], 3, 9)[0].vwap, null);
});

test('EMA: seeded with the first close, k = 2/(n+1)', () => {
  const b = bars([tick('10:00', 100), tick('10:03', 110), tick('10:06', 120)], 3, 3);
  assert.equal(b[0].ema, 100);
  assert.equal(b[1].ema, 105);
  assert.equal(b[2].ema, 112.5);
});

test('a bar is complete only once its end plus the grace has passed', () => {
  const b = bars([tick('10:00', 1), tick('10:03', 1)], 3, 9);
  assert.equal(closedBars(b, '10:03:10', 20).length, 0);
  assert.equal(closedBars(b, '10:03:20', 20).length, 1);
  assert.equal(closedBars(b, '10:06:30', 20).length, 2);
});

// A day: the price drifts down under VWAP, then rallies hard (EMA crosses up), then fades.
function day(shape: number[]): Tick[] {
  return shape.map((p, i) => {
    const m = 600 + i * 3; // 10:00, 10:03, …
    return tick(`${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`, p, 1000);
  });
}
const DATE = '2026-10-21'; // a Wednesday
const P = S.params({});

test('cross up → buy; a close under the EMA → sell', () => {
  const b = bars(day([100, 99, 98, 97, 96, 95, 94, 93, 92, 91, 95, 100, 104, 106, 105, 100, 96]), 3, 9);
  const sig = S.replay(b, DATE, P);
  assert.equal(sig[0].side, 'buy');
  assert.equal(sig[0].reason, 'cross-up');
  const i = b.findIndex((x) => x.t === sig[0].t);
  assert.ok(b[i - 1].ema <= b[i - 1].vwap! && b[i].ema > b[i].vwap!);
  assert.equal(sig[1].side, 'sell');
  assert.ok(['below-ema', 'stop'].includes(sig[1].reason));
  assert.equal(sig.length, 2);
});

test('no entry before minBars bars exist', () => {
  const b = bars(day([100, 90, 110, 120]), 3, 9);
  assert.deepEqual(S.replay(b, DATE, P), []);
});

test('stop: a close 1.5% under the entry sells even above the EMA', () => {
  const b = bars(day([100, 100, 100, 100, 100, 100, 100, 100, 100, 101]), 3, 9);
  const entry = { entry: 10_000, date: DATE };
  const at = (c: number) => ({ ...b[9], c, ema: 0 });
  assert.deepEqual(S.decide([...b.slice(0, 9), at(9_850)], 9, entry, DATE, P), { side: 'sell', reason: 'stop' });
  assert.equal(S.decide([...b.slice(0, 9), at(9_860)], 9, entry, DATE, P), null);
});

test('session: no entry after lastEntry, out at flatAt, Friday is shorter; yesterday\'s holding is sold', () => {
  assert.deepEqual(S.session('2026-10-23', P), { lastEntry: '13:00', flatAt: '13:30' }); // Friday
  assert.deepEqual(S.session(DATE, P), { lastEntry: '16:30', flatAt: '17:00' });
  const late = { t: '17:00', end: '17:03', o: 1, h: 1, l: 1, c: 100, v: 1, vwap: 90, ema: 95 };
  assert.deepEqual(S.decide([late], 0, { entry: 100, date: DATE }, DATE, P), { side: 'sell', reason: 'end-of-day' });
  const mid = { ...late, t: '11:00', end: '11:03' };
  assert.deepEqual(S.decide([mid], 0, { entry: 100, date: '2026-10-20' }, DATE, P), { side: 'sell', reason: 'end-of-day' });
  assert.equal(S.decide([mid], 0, { entry: 100, date: DATE }, DATE, P), null);
});

test('params: overrides are validated', () => {
  assert.equal(S.params({ stopPct: 2 }).stopPct, 2);
  assert.throws(() => S.params({ stopPct: 0 }));
  assert.throws(() => S.params({ budget: 1.5 }));
  assert.throws(() => S.params({ flatAt: '5pm' }));
});

// ── the account side ────────────────────────────────────────────────────────

const NOW = new Date('2026-10-21T10:00:00Z'); // 13:00 Israel
const quote = (over: Partial<TradeQuote> = {}): TradeQuote => ({
  id: '629014', name: 'טבע', symbol: 'TEVA', type: 'מניות', companyId: '629', last: 12_000, open: 12_000,
  tradeDate: '2026-10-21', tradeTime: '13:00', marketOpen: true, suspended: false, ...over,
});
const by = { strategy: S.STRATEGY, signal: 'x:1' };

test('a strategy order is tagged, its trade too, and one signal places one order', () => {
  const { account, order } = A.place(A.fresh(NOW), 'buy', 5, quote(), NOW, by);
  assert.deepEqual(order.by, by);
  assert.deepEqual(account.trades[0].by, by);
  assert.throws(() => A.place(account, 'buy', 5, quote(), NOW, by), new RegExp(A.DUPLICATE_SIGNAL));
  assert.equal(A.place(A.fresh(NOW), 'buy', 5, quote(), NOW).order.by, null, 'the owner\'s orders carry no tag');
});

test('a strategy order never waits for the opening', () => {
  assert.throws(() => A.place(A.fresh(NOW), 'buy', 5, quote({ marketOpen: false, tradeTime: null }), NOW, by), /לא נסחר/);
});

// ── the files ───────────────────────────────────────────────────────────────

test('recording a day merges by trade number', async () => {
  assert.equal(await T.recordDay('629014', DATE, [tick('10:00', 1), tick('10:01', 2)]), 2);
  const had = await T.readDay('629014', DATE);
  assert.equal(await T.recordDay('629014', DATE, had.slice(0, 1)), 0, 'a shorter answer loses nothing');
  assert.equal((await T.readDay('629014', DATE)).length, 2);
  assert.deepEqual(await T.recordedDays('629014'), [DATE]);
  await assert.rejects(T.readDay('../x', DATE));
});

test('the file lock: one holder at a time, across processes; a dead holder\'s lock is taken over', async () => {
  // Another process holds it for 400 ms…
  const child = spawn(process.execPath, ['--disable-warning=MODULE_TYPELESS_PACKAGE_JSON', '-e', `
    const { withFileLock } = await import(${JSON.stringify(path.resolve('lib/store.ts'))});
    await withFileLock('a.json', async () => { console.log('in'); await new Promise(r => setTimeout(r, 400)); });
  `, '--input-type=module'], { env: process.env });
  await new Promise<void>((r) => child.stdout.once('data', () => r()));
  const t0 = Date.now();
  await withFileLock('a.json', async () => {});
  assert.ok(Date.now() - t0 >= 250, 'waited for the other process');
  // A lock left by a process that no longer exists does not block.
  writeFileSync(dataFile('b.json.lock'), `999999 ${Date.now()}`);
  const t1 = Date.now();
  await withFileLock('b.json', async () => {});
  assert.ok(Date.now() - t1 < 1000);
});

test('outcome: round trips sized by the budget, with the account\'s fees on both sides', async () => {
  const { outcome } = await import('../lib/strategy/outcome.ts');
  // ₪10,000 at 100 agorot = 10,000 units; sold at 102 → gross gain ₪200, fees ₪10 + ₪10.2
  const o = outcome([{ t: '10:30', side: 'buy', reason: 'cross-up', price: 100 }, { t: '11:00', side: 'sell', reason: 'below-ema', price: 102 }], null, P);
  assert.deepEqual([o.trips, o.wins, o.open], [1, 1, false]);
  assert.equal(o.pnl, 20_000 - 1_000 - 1_020);
  // A position still open at the end of the record is valued at the last close.
  const open = outcome([{ t: '10:30', side: 'buy', reason: 'cross-up', price: 100 }], 99, P);
  assert.equal(open.open, true);
  assert.ok(open.pnl < 0);
});
