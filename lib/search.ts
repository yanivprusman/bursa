// Search over everything that trades on TASE, by Hebrew name, English name,
// symbol, ISIN or security number.
//
// TASE publishes its whole search index as one ~2 MB list per language; the site
// downloads it into the browser. We hold it here instead, so a phone on mobile
// data sends a few bytes and gets back a few rows.

import { bareId, tase } from './tase';
import type { Kind } from './market';

type RawEntity = {
  Id: string;
  Name: string | null;
  Smb: string | null;
  ISIN: string | null;
  /** 1 = traded security, 2 = index. The rest (companies, funds, reports) are not quotable here. */
  Type: number;
  SubTypeDesc: string | null;
};

export type Hit = {
  kind: Kind;
  id: string;
  name: string;
  symbol: string | null;
  type: string | null;
  isin: string | null;
};

type Row = Hit & { keys: string[]; rank: number };

const HOUR = 60 * 60 * 1000;

/** Lower-case, and drop the punctuation people never type: ת"א-35 → תא 35. */
export function fold(s: string): string {
  return s
    .toLowerCase()
    .replace(/["'״׳`.]/g, '')
    .replace(/[-_/\\()]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Shares first, then indices and ETFs, then the long tail of bonds and warrants. */
function typeRank(kind: Kind, type: string | null): number {
  if (kind === 'index') return 1;
  const t = type ?? '';
  if (t.startsWith('מניות')) return 0;
  if (t.startsWith('קרן סל') || t.startsWith('קרן חוץ')) return 2;
  if (t.startsWith('יחידת השתתפות')) return 3;
  return 4;
}

let built: { at: number; rows: Row[] } | null = null;

async function rows(): Promise<Row[]> {
  if (built && Date.now() - built.at < 12 * HOUR) return built.rows;
  const [he, en] = await Promise.all([
    tase<RawEntity[]>('api', 'content/searchentities?lang=0', 12 * HOUR),
    tase<RawEntity[]>('api', 'content/searchentities?lang=1', 12 * HOUR),
  ]);
  const english = new Map<string, RawEntity>();
  for (const e of en) if (e.Type === 1 || e.Type === 2) english.set(`${e.Type}:${e.Id}`, e);

  const out: Row[] = [];
  for (const e of he) {
    if ((e.Type !== 1 && e.Type !== 2) || !e.Name) continue;
    const kind: Kind = e.Type === 1 ? 'security' : 'index';
    const eng = english.get(`${e.Type}:${e.Id}`);
    const type = e.Type === 2 ? 'מדד' : e.SubTypeDesc?.trim() || null;
    const keys = [e.Name, e.Smb, eng?.Name, eng?.Smb]
      .filter((k): k is string => Boolean(k))
      .map(fold)
      .filter((k, i, all) => k && all.indexOf(k) === i);
    out.push({
      kind,
      id: bareId(e.Id),
      name: e.Name.trim(),
      symbol: e.Smb?.trim() || null,
      type,
      isin: e.ISIN,
      keys,
      rank: typeRank(kind, type),
    });
  }
  built = { at: Date.now(), rows: out };
  return out;
}

/** 0 = exact, 1 = starts with, 2 = a word starts with, 3 = contains; -1 = no match. */
function score(row: Row, q: string): number {
  let best = -1;
  const better = (s: number) => {
    if (best === -1 || s < best) best = s;
  };
  for (const k of row.keys) {
    if (k === q) return 0;
    if (k.startsWith(q)) better(1);
    else if (k.includes(' ' + q)) better(2);
    else if (k.includes(q)) better(3);
  }
  if (/^\d+$/.test(q) && row.id.startsWith(q)) better(row.id === q ? 0 : 1);
  if (row.isin && q.length >= 4 && row.isin.toLowerCase().startsWith(q)) better(1);
  return best;
}

export async function search(query: string, limit = 40): Promise<Hit[]> {
  const q = fold(query);
  if (!q) return [];
  const hits: Array<{ row: Row; s: number }> = [];
  for (const row of await rows()) {
    const s = score(row, q);
    if (s >= 0) hits.push({ row, s });
  }
  hits.sort(
    (a, b) =>
      a.s - b.s ||
      a.row.rank - b.row.rank ||
      a.row.name.length - b.row.name.length ||
      a.row.name.localeCompare(b.row.name, 'he'),
  );
  return hits.slice(0, limit).map(({ row }) => ({
    kind: row.kind,
    id: row.id,
    name: row.name,
    symbol: row.symbol,
    type: row.type,
    isin: row.isin,
  }));
}
