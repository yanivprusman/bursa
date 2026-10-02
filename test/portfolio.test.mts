import assert from 'node:assert/strict';
import { test } from 'node:test';
import { holdingValue, totals } from '../app/_ui/portfolio.ts';
import * as F from '../app/_ui/format.ts';

const near = (expected: number, actual: number | null) => assert.ok(actual !== null && Math.abs(expected - actual) < 1e-6, `expected ${expected}, got ${actual}`);

test('prices are agorot: 100 Teva at 12,160 is 12,160 shekels', () => {
  const v = holdingValue(100, 10000, 12160, 12310);
  near(12160, v.value);
  near(-150, v.dayChange);
  near(10000, v.cost);
  near(2160, v.gain);
  near(21.6, v.gainPct);
});

test('no cost means no gain; no previous close means no day change', () => {
  const v = holdingValue(10, null, 500, 480);
  near(50, v.value);
  assert.equal(v.gain, null);
  assert.equal(holdingValue(10, null, 500, null).dayChange, null);
});

test('totals add up, and the day % is against yesterday\'s worth', () => {
  const t = totals([holdingValue(100, 10000, 12160, 12310), holdingValue(50, 8000, 7522, 7400)]);
  near(15921, t.value);
  near(-89, t.dayChange);
  near((-89 / 16010) * 100, t.dayChangePct);
  near(1921, t.gain);
  near((1921 / 14000) * 100, t.gainPct);
  assert.equal(t.gainIsPartial, false);
});

test('gain is partial when a cost is missing; an empty portfolio has none', () => {
  assert.equal(totals([holdingValue(100, 10000, 12160, 12310), holdingValue(10, null, 500, 480)]).gainIsPartial, true);
  const empty = totals([]);
  assert.equal(empty.value, 0);
  assert.equal(empty.gain, null);
  assert.equal(empty.dayChangePct, null);
});

test('formatting matches the phone', () => {
  assert.equal(F.fixed(1234567.891, 2), '1,234,567.89');
  assert.equal(F.fixed(-0.001, 2), '0.00');
  assert.equal(F.price(12160, 'agorot'), '12,160');
  assert.equal(F.price(104.5, 'agorot'), '104.5');
  assert.equal(F.price(4218, 'points'), '4,218.00');
  assert.equal(F.pct(0.34), '+0.34%');
  assert.equal(F.pct(-1.22), '-1.22%');
  assert.equal(F.pct(-0.004), '0.00%');
  assert.equal(F.shekels(1234.5), '₪1,234.50');
  assert.equal(F.shekels(12345.67), '₪12,346');
  assert.equal(F.signedShekels(-1930), '-₪1,930.00');
  assert.equal(F.signedShekels(0), '₪0.00');
  assert.equal(F.bigShekels(2_267_733_000_000), '2.27 טריליון ₪');
  assert.equal(F.bigShekels(141_700_004_000), '141.7 מיליארד ₪');
  assert.equal(F.bigShekels(60_800), '61 אלף ₪');
  assert.equal(F.date('2026-10-01'), '1.10.2026');
  assert.equal(F.monthYear('2026-10-01'), '10/26');
  assert.equal(F.parse('1,250.5'), 1250.5);
  assert.equal(F.parse('-5'), null);
  assert.equal(F.parse(''), null);
});
