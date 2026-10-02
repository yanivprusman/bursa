import assert from 'node:assert/strict';
import { mkdtempSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

// Every test writes to a throwaway directory — never to the owner's real account.
process.env.BURSA_DATA_DIR = mkdtempSync(path.join(tmpdir(), 'bursa-account-'));
const A = await import('../lib/account.ts');
const { StoreError } = await import('../lib/store.ts');
type TradeQuote = import('../lib/market.ts').TradeQuote;

// Thursday 1 Oct 2026, 13:00 in Israel (UTC+3): mid-session.
const THU_1300 = new Date('2026-10-01T10:00:00Z');
// Thursday 1 Oct 2026, 20:00 in Israel: the day has closed.
const THU_2000 = new Date('2026-10-01T17:00:00Z');
// Monday 5 Oct 2026, 08:00 in Israel: before the opening.
const MON_0800 = new Date('2026-10-05T05:00:00Z');
const MON_1100 = new Date('2026-10-05T08:00:00Z');

const quote = (over: Partial<TradeQuote> = {}): TradeQuote => ({
  id: '629014',
  name: 'טבע',
  symbol: 'TEVA',
  type: 'מניות',
  companyId: '629',
  last: 12160,
  open: 12200,
  tradeDate: '2026-10-01',
  tradeTime: '13:00',
  marketOpen: true,
  suspended: false,
  ...over,
});

test('fees: 0.1% of the deal, at least ₪5', () => {
  assert.equal(A.feeOf(100_000), 500); // ₪1,000 → the minimum
  assert.equal(A.feeOf(10_000_000), 10_000); // ₪100,000 → ₪100
  assert.equal(A.feeOf(1_000_001), 1001); // rounds up to the agora
});

test('a buy while the paper trades live fills now, at the last price', () => {
  const { account, order } = A.place(A.fresh(THU_1300), 'buy', 10, quote(), THU_1300);
  assert.equal(order.status, 'filled');
  const t = account.trades[0];
  assert.equal(t.price, 12160);
  assert.equal(t.how, 'live');
  assert.equal(t.gross, 121_600);
  assert.equal(t.fee, 500);
  assert.equal(account.cash, A.RULES.startCash - 121_600 - 500);
  const p = A.positions(account.trades).positions[0];
  assert.equal(p.qty, 10);
  assert.equal(p.cost, 122_100, 'cost includes the fee');
});

test('after the close, an order waits for the NEXT day\'s opening, not today\'s', () => {
  const closed = quote({ marketOpen: false, tradeTime: null });
  const { account, order } = A.place(A.fresh(THU_2000), 'buy', 10, closed, THU_2000);
  assert.equal(order.status, 'pending');
  assert.equal(order.fillFrom, '2026-10-02');
  assert.equal(account.trades.length, 0);
  assert.equal(account.cash, A.RULES.startCash, 'nothing is paid until it fills');
  assert.equal(A.available(account), A.RULES.startCash - order.reserve);
  // Read again the same evening: the quote is still Thursday's, so nothing fills.
  assert.equal(A.settle(account, new Map([['629014', closed]]), THU_2000), account);
  // Monday morning, open: the opening price fills it.
  const monday = quote({ tradeDate: '2026-10-05', open: 11900, last: 12050, tradeTime: '10:00' });
  const after = A.settle(account, new Map([['629014', monday]]), MON_1100);
  assert.equal(after.trades[0].price, 11900);
  assert.equal(after.trades[0].how, 'open');
  assert.equal(after.trades[0].tradeDate, '2026-10-05');
  assert.equal(after.orders[0].status, 'filled');
  assert.equal(after.orders[0].reserve, 0);
});

test('before the opening, an order fills at that same day\'s opening', () => {
  const preOpen = quote({ marketOpen: false, tradeTime: null }); // still Thursday's data
  const { order, account } = A.place(A.fresh(MON_0800), 'buy', 1, preOpen, MON_0800);
  assert.equal(order.fillFrom, '2026-10-05');
  const after = A.settle(account, new Map([['629014', quote({ tradeDate: '2026-10-05', open: 12000 })]]), MON_1100);
  assert.equal(after.trades[0].price, 12000);
});

test('the market is open but the paper has not traded today: it waits for its first price', () => {
  const quiet = quote({ tradeDate: '2026-09-30', tradeTime: null });
  const { order } = A.place(A.fresh(THU_1300), 'buy', 1, quiet, THU_1300);
  assert.equal(order.status, 'pending');
  assert.equal(order.fillFrom, '2026-10-01');
});

test('cash and units are checked: no buying past the cash, no selling what is not held', () => {
  const a = A.fresh(THU_1300);
  assert.throws(() => A.place(a, 'buy', 1_000, quote(), THU_1300), /מזומן/); // ₪121,600 > ₪100,000
  assert.throws(() => A.place(a, 'sell', 1, quote(), THU_1300), /יחידות/);
  // A waiting buy holds its cash back from the next one.
  const closed = quote({ marketOpen: false, tradeTime: null });
  const first = A.place(a, 'buy', 700, closed, THU_2000).account; // ~₪85k reserved
  assert.throws(() => A.place(first, 'buy', 200, closed, THU_2000), /מזומן/);
});

test('bad input is refused', () => {
  const a = A.fresh(THU_1300);
  for (const qty of [0, -1, 1.5, '10', null, Infinity]) assert.throws(() => A.place(a, 'buy', qty, quote(), THU_1300), StoreError);
  assert.throws(() => A.place(a, 'short', 1, quote(), THU_1300), StoreError);
  assert.throws(() => A.place(a, 'buy', 1, quote({ suspended: true }), THU_1300), /מושעה/);
});

test('selling realises gain against the average cost, fees on both sides', () => {
  let a = A.place(A.fresh(THU_1300), 'buy', 10, quote({ last: 10_000 }), THU_1300).account; // cost 100,000 + 500
  a = A.place(a, 'buy', 10, quote({ last: 12_000 }), THU_1300).account; // cost 120,000 + 500
  a = A.place(a, 'sell', 5, quote({ last: 13_000 }), THU_1300).account; // proceeds 65,000 − 500
  const { positions, realized, fees } = A.positions(a.trades);
  assert.equal(positions[0].qty, 15);
  assert.equal(fees, 1500);
  // avg cost 221,000 / 20 = 11,050 per unit; 5 units = 55,250 → realised 64,500 − 55,250
  assert.equal(realized, 9250);
  assert.equal(a.cash, A.RULES.startCash - 100_500 - 120_500 + 64_500);
  a = A.place(a, 'sell', 15, quote({ last: 13_000 }), THU_1300).account;
  assert.equal(A.positions(a.trades).positions.length, 0, 'a sold-out paper is no longer held');
});

test('a waiting buy that the opening price makes too expensive is rejected, not filled into debt', () => {
  const closed = quote({ marketOpen: false, tradeTime: null, last: 10_000 });
  const { account } = A.place(A.fresh(THU_2000), 'buy', 990, closed, THU_2000); // ₪99,000 + fee fits ₪100k
  const gapUp = quote({ tradeDate: '2026-10-02', open: 11_000 });
  const after = A.settle(account, new Map([['629014', gapUp]]), MON_1100);
  assert.equal(after.trades.length, 0);
  assert.equal(after.orders[0].status, 'rejected');
  assert.equal(after.cash, A.RULES.startCash);
});

test('a waiting order can be cancelled, once', () => {
  const closed = quote({ marketOpen: false, tradeTime: null });
  const { account, order } = A.place(A.fresh(THU_2000), 'buy', 1, closed, THU_2000);
  const c = A.cancel(account, order.id, THU_2000);
  assert.equal(c.orders[0].status, 'cancelled');
  assert.equal(A.available(c), A.RULES.startCash);
  assert.throws(() => A.cancel(c, order.id, THU_2000), /ממתינה/);
});

test('the file: an order is saved, a reset keeps the old account beside the new one', async () => {
  const quoter = async () => quote();
  const r = await A.operate({ op: 'order', side: 'buy', id: '629014', qty: 3 }, quoter, () => THU_1300);
  assert.equal(r.order?.status, 'filled');
  assert.equal(r.rev, 1);
  assert.equal(r.positions[0].qty, 3);
  const read = await A.readAccount(quoter, () => THU_1300);
  assert.equal(read.rev, 1, 'reading with nothing to fill does not write');
  await assert.rejects(A.operate({ op: 'reset' }, quoter, () => THU_1300), /אישור/);
  const reset = await A.operate({ op: 'reset', confirm: true }, quoter, () => THU_2000);
  assert.equal(reset.cash, A.RULES.startCash);
  assert.equal(reset.trades.length, 0);
  assert.ok(readdirSync(process.env.BURSA_DATA_DIR!).some((f) => f.startsWith('account-until-')));
});

test('a waiting order fills on the next read once its paper has opened', async () => {
  let q = quote({ marketOpen: false, tradeTime: null });
  const quoter = async () => q;
  await A.operate({ op: 'order', side: 'buy', id: '629014', qty: 2 }, quoter, () => THU_2000);
  q = quote({ tradeDate: '2026-10-05', open: 11_111 });
  const read = await A.readAccount(quoter, () => MON_1100);
  assert.equal(read.pending.length, 0);
  assert.equal(read.trades[0].price, 11_111);
});
