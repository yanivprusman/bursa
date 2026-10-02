import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

// Every test writes to a throwaway directory — never to the owner's real list.
process.env.BURSA_DATA_DIR = mkdtempSync(path.join(tmpdir(), 'bursa-list-'));
const { apply, change, readList, ListError } = await import('../lib/list.ts');

const teva = { kind: 'security', id: '629014', name: 'טבע', symbol: 'טבע', type: 'מניות', companyId: '629' };
const ta35 = { kind: 'index', id: '142', name: 'ת"א-35', type: 'מדד' };

test('an empty directory is an empty list at revision 0', async () => {
  assert.deepEqual(await readList(), { rev: 0, updatedAt: null, items: [] });
});

test('follow adds once; following again changes nothing', async () => {
  const a = await change({ op: 'follow', item: teva });
  assert.equal(a.rev, 1);
  assert.equal(a.items.length, 1);
  assert.equal(a.items[0].qty, null);
  const b = await change({ op: 'follow', item: teva });
  assert.equal(b.rev, 1, 'a no-op must not bump the revision');
});

test('hold sets a holding and keeps the name the paper was followed under', async () => {
  const l = await change({ op: 'hold', item: { ...teva, name: 'other name' }, qty: 50, avgCost: 10000 });
  assert.equal(l.items[0].qty, 50);
  assert.equal(l.items[0].avgCost, 10000);
  assert.equal(l.items[0].name, 'טבע');
});

test('hold without a purchase price is a value with no gain', async () => {
  const l = await change({ op: 'hold', item: teva, qty: 10 });
  assert.equal(l.items[0].qty, 10);
  assert.equal(l.items[0].avgCost, null);
});

test('an index can be followed but not held', async () => {
  await change({ op: 'follow', item: ta35 });
  await assert.rejects(change({ op: 'hold', item: ta35, qty: 1 }), ListError);
});

test('bad quantities are refused and nothing is written', async () => {
  const before = (await readList()).rev;
  for (const qty of [0, -5, 'ten', null, Infinity]) {
    await assert.rejects(change({ op: 'hold', item: teva, qty }), ListError);
  }
  assert.equal((await readList()).rev, before);
});

test('clearHolding keeps following; unfollow removes', async () => {
  const cleared = await change({ op: 'clearHolding', kind: 'security', id: '629014' });
  assert.equal(cleared.items.find((x) => x.id === '629014')?.qty, null);
  const gone = await change({ op: 'unfollow', kind: 'security', id: '629014' });
  assert.equal(gone.items.some((x) => x.id === '629014'), false);
});

test('import takes only what is not here yet and never changes what is', async () => {
  await change({ op: 'hold', item: teva, qty: 7 });
  const l = await change({
    op: 'import',
    items: [
      { ...teva, qty: 999, avgCost: 1 },
      { kind: 'security', id: '604611', name: 'לאומי', qty: 100, avgCost: 7000 },
    ],
  });
  assert.equal(l.items.find((x) => x.id === '629014')?.qty, 7);
  assert.equal(l.items.find((x) => x.id === '604611')?.qty, 100);
});

test('two changes at once both land', async () => {
  const start = (await readList()).rev;
  await Promise.all([
    change({ op: 'follow', item: { kind: 'security', id: '1', name: 'a' } }),
    change({ op: 'follow', item: { kind: 'security', id: '2', name: 'b' } }),
  ]);
  const l = await readList();
  assert.equal(l.rev, start + 2);
  assert.ok(l.items.some((x) => x.id === '1') && l.items.some((x) => x.id === '2'));
});

test('a damaged file is reported, never overwritten', async () => {
  const f = path.join(process.env.BURSA_DATA_DIR!, 'list.json');
  const good = readFileSync(f, 'utf8');
  writeFileSync(f, '{ not json');
  await assert.rejects(change({ op: 'follow', item: { kind: 'security', id: '3', name: 'c' } }), /damaged/);
  assert.equal(readFileSync(f, 'utf8'), '{ not json');
  writeFileSync(f, good);
});

test('apply is pure: an unknown op throws, a no-op returns the same array', () => {
  const items = [{ ...teva, qty: null, avgCost: null }] as Parameters<typeof apply>[0];
  assert.throws(() => apply(items, { op: 'nonsense' }), ListError);
  assert.equal(apply(items, { op: 'follow', item: teva }), items);
});
