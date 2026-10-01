// TASE's raw JSON → the shapes the phone app reads (see API.md).
//
// Prices are passed through in TASE's own unit: agorot for every traded security,
// points for an index. Nothing here converts to shekels — a holding's worth is
// quantity × price / 100 and the phone does that, so one number has one meaning.

import { TaseError, bareId, clock, isoDate, num, paddedId, prose, tase, tidy } from './tase';

export type Kind = 'security' | 'index';

export type Quote = {
  kind: Kind;
  id: string;
  name: string;
  symbol: string | null;
  /** Hebrew type label as TASE words it ("מניות", "קרן סל מניות", "מדד"…). */
  type: string | null;
  last: number;
  /** Previous close. */
  base: number | null;
  /** Absolute move since the previous close, in the same unit as `last`. */
  change: number | null;
  changePct: number | null;
  unit: 'agorot' | 'points';
  tradeDate: string | null;
  /** "HH:MM" of the last trade while the session runs; null once it is end-of-day. */
  tradeTime: string | null;
};

const SEC = 1000;
const MIN = 60 * SEC;
const HOUR = 60 * MIN;

// ── market status drives every TTL ──────────────────────────────────────────

type RawStatus = { isMarketOpen: boolean; isInDayForIndices: boolean; isInDayForSecurities: boolean };

export async function isOpen(): Promise<boolean> {
  const s = await tase<RawStatus>('micro', 'tradestatus/tradestatus', 30 * SEC);
  return Boolean(s.isMarketOpen);
}

/** Live numbers: short while trading, long once the day is settled. */
async function liveTtl(): Promise<number> {
  return (await isOpen()) ? 20 * SEC : 5 * MIN;
}

function moved(last: number, base: number | null) {
  return base === null ? null : Math.round((last - base) * 100) / 100;
}

// ── indices ─────────────────────────────────────────────────────────────────

type RawIndex = {
  Id: string;
  Name: string;
  LastRate: number;
  Change: number;
  Turnover: number | null;
  Gainers: number | null;
  Decliners: number | null;
  NoChanges: number | null;
  TradeDate: string;
  TradeTime: string | null;
  IndexCategoryType: string;
};

const INDEX_CATEGORIES: Record<string, string> = {
  '02': 'מדדי שווי שוק',
  '03': 'מדדים ענפיים',
  '04': 'מדדים נוספים',
  '08': 'תל בונד',
  '16': 'תנודתיות',
  '17': 'All-Bond',
  '18': 'תל גוב',
};

const CATEGORY_ORDER = ['02', '03', '04', '16', '08', '17', '18'];

export type IndexRow = Quote & {
  category: string;
  gainers: number | null;
  decliners: number | null;
  unchanged: number | null;
  /** Thousands of shekels. */
  turnover: number | null;
};

function indexRow(r: RawIndex): IndexRow {
  return {
    kind: 'index',
    id: bareId(r.Id),
    name: tidy(r.Name),
    symbol: null,
    type: 'מדד',
    last: r.LastRate,
    // The list carries the % move but not the previous close. Deriving one from
    // a rounded percentage would be a few hundredths off, so it stays unknown
    // here; index() reads the real one.
    base: null,
    change: null,
    changePct: num(r.Change),
    unit: 'points',
    tradeDate: isoDate(r.TradeDate),
    tradeTime: clock(r.TradeTime),
    category: INDEX_CATEGORIES[r.IndexCategoryType] ?? 'מדדים נוספים',
    gainers: num(r.Gainers),
    decliners: num(r.Decliners),
    unchanged: num(r.NoChanges),
    turnover: num(r.Turnover),
  };
}

export async function allIndices(): Promise<IndexRow[]> {
  const raw = await tase<RawIndex[]>('api', 'index/getindayindices?lang=0', await liveTtl());
  // Share indices before bond indices; within a category, the exchange's own order.
  const rank = (code: string) => {
    const i = CATEGORY_ORDER.indexOf(code);
    return i < 0 ? CATEGORY_ORDER.length : i;
  };
  return raw
    .map((r, n) => ({ r, n }))
    .sort((a, b) => rank(a.r.IndexCategoryType) - rank(b.r.IndexCategoryType) || a.n - b.n)
    .map(({ r }) => indexRow(r));
}

// ── overview ────────────────────────────────────────────────────────────────

type RawMover = { Id: string; Name: string; LastRate: number; Change: number; Value: number };
type RawMovers = {
  'he-IL': {
    TradeDate: string;
    TradeTime: string | null;
    TurnOvers: { Items: RawMover[] };
    Gainers: { Items: RawMover[] };
    Declainers: { Items: RawMover[] };
  };
};
type RawTurnovers = { turnoversItems: Array<{ type: string; name: string; value: number }> };

export type Mover = {
  id: string;
  name: string;
  last: number;
  changePct: number;
  /** Thousands of shekels; only on the "most traded" list. */
  turnover: number | null;
};

/** The headline indices, in the order they are shown. */
const HEADLINE = ['142', '137', '143', '147', '194'];

export async function overview() {
  const ttl = await liveTtl();
  const [open, indices, movers, turnovers, sparks] = await Promise.all([
    isOpen(),
    allIndices(),
    tase<RawMovers>('api', 'marketdata/mostactive', ttl),
    tase<RawTurnovers>('micro', 'overallturnovers/overallturnovers', ttl),
    // A month of closes per headline index: the shape behind today's number.
    Promise.all(HEADLINE.map((id) => chart('index', id, '1m'))),
  ]);

  const byId = new Map(indices.map((i) => [i.id, i]));
  const headline = HEADLINE.flatMap((id, n) => {
    const row = byId.get(id);
    return row ? [{ ...row, spark: sparks[n].points.map((p) => p.v) }] : [];
  });

  const m = movers['he-IL'];
  const mover = (withTurnover: boolean) => (r: RawMover): Mover => ({
    id: bareId(r.Id),
    name: tidy(r.Name),
    last: r.LastRate,
    changePct: r.Change,
    turnover: withTurnover ? r.Value : null,
  });

  const ta125 = byId.get('137');
  return {
    open,
    tradeDate: ta125?.tradeDate ?? isoDate(m.TradeDate),
    tradeTime: ta125?.tradeTime ?? clock(m.TradeTime),
    indices: headline,
    /** Movers are TASE's own lists, drawn from ת"א-125. */
    movers: {
      gainers: m.Gainers.Items.map(mover(false)),
      losers: m.Declainers.Items.map(mover(false)),
      active: m.TurnOvers.Items.map(mover(true)),
    },
    breadth: ta125
      ? { gainers: ta125.gainers, decliners: ta125.decliners, unchanged: ta125.unchanged }
      : null,
    /** Thousands of shekels per market segment. */
    turnovers: (turnovers.turnoversItems ?? []).map((t) => ({ name: t.name, value: t.value })),
  };
}

// ── one security ────────────────────────────────────────────────────────────

type RawSecurity = {
  Id: string;
  Name: string;
  Symbol: string | null;
  SecurityLongName: string | null;
  Type: string | null;
  SecuritySubType: string | null;
  LastRate: number | null;
  Change: number | null;
  PointsChange: number | null;
  BaseRate: number | null;
  OpenRate: number | null;
  HighRate: number | null;
  LowRate: number | null;
  TradeDate: string | null;
  TradeTime: string | null;
  LastDealTime: string | null;
  MarketValue: number | null;
  TurnOverValueShekel: number | null;
  OverallTurnOverUnits: number | null;
  DealsNo: number | null;
  MonthYield: number | null;
  AnnualYield: number | null;
  CompanyId: number | null;
  CompanyName: string | null;
  FullBranch: string | null;
  ISIN: string | null;
  BrutoYield: number | null;
  RedemptionDate: string | null;
  Linkage: string | null;
  AnnualInterest: number | null;
};

async function rawSecurity(id: string): Promise<RawSecurity> {
  const raw = await tase<RawSecurity | null>(
    'api',
    `company/securitydata?securityId=${bareId(id)}&lang=0`,
    await liveTtl(),
  );
  if (!raw || raw.LastRate === null || raw.LastRate === undefined || !raw.Name) {
    throw new TaseError(`נייר ${bareId(id)} לא נמצא בבורסה`, 404);
  }
  return raw;
}

function securityQuote(r: RawSecurity): Quote {
  const last = r.LastRate as number;
  return {
    kind: 'security',
    id: bareId(r.Id),
    name: tidy(r.Name),
    symbol: r.Symbol?.trim() || null,
    type: r.Type?.trim() || null,
    last,
    base: num(r.BaseRate),
    change: num(r.PointsChange) ?? moved(last, num(r.BaseRate)),
    changePct: num(r.Change),
    unit: 'agorot',
    tradeDate: isoDate(r.TradeDate),
    tradeTime: clock(r.TradeTime) ?? clock(r.LastDealTime),
  };
}

type RawMajor = {
  CompanyDetails: { Description: string | null; Site: string | null } | null;
};

export async function security(id: string) {
  const r = await rawSecurity(id);
  // The company blurb lives on a second endpoint; it changes about never.
  let about: string | null = null;
  let site: string | null = null;
  if (r.CompanyId) {
    const major = await tase<RawMajor>(
      'api',
      `security/majordata?secId=${bareId(id)}&compId=${r.CompanyId}&lang=0`,
      6 * HOUR,
    );
    about = prose(major.CompanyDetails?.Description);
    site = major.CompanyDetails?.Site?.trim().toLowerCase() || null;
  }
  return {
    ...securityQuote(r),
    longName: tidy(r.SecurityLongName) || null,
    subType: r.SecuritySubType?.trim() || null,
    sector: r.FullBranch?.trim() || null,
    isin: r.ISIN,
    open: num(r.OpenRate),
    high: num(r.HighRate),
    low: num(r.LowRate),
    /** Thousands of shekels. */
    marketCap: num(r.MarketValue),
    /** Shekels. */
    turnover: num(r.TurnOverValueShekel),
    volume: num(r.OverallTurnOverUnits),
    deals: num(r.DealsNo),
    monthYield: num(r.MonthYield),
    yearYield: num(r.AnnualYield),
    /** Bonds only. */
    grossYield: num(r.BrutoYield),
    redemptionDate: isoDate(r.RedemptionDate),
    linkage: r.Linkage?.trim() || null,
    annualInterest: num(r.AnnualInterest),
    about,
    site,
  };
}

// ── one index, with what is in it ───────────────────────────────────────────

type RawIndexDetails = RawIndex & {
  Description: string | null;
  BaseRate: number | null;
  OpenRate: number | null;
  HighRate: number | null;
  LowRate: number | null;
  MonthYield: number | null;
  AnnualYield: number | null;
  MarketValue: number | null;
};

type Paged<T> = { Items: T[]; TotalRec: number };
type RawComponentQuote = {
  ISIN_ID: string;
  Id: string;
  Name: string;
  Symbol: string | null;
  LastRate: number | null;
  Change: number | null;
  TurnOverValue: number | null;
};
type RawComponentWeight = { SecurityNumber: string; Weight: number | null };

const PAGE = 30;

/** Read every page of a paged POST endpoint. */
async function allPages<T>(path: string, oId: string, ttl: number): Promise<T[]> {
  const body = (pageNum: number) => ({ dType: 1, TotalRec: 1, pageNum, oId, lang: '0' });
  const first = await tase<Paged<T>>('api', path, ttl, body(1));
  const pages = Math.ceil((first.TotalRec ?? first.Items.length) / PAGE);
  const rest = await Promise.all(
    Array.from({ length: Math.max(0, pages - 1) }, (_, i) =>
      tase<Paged<T>>('api', path, ttl, body(i + 2)),
    ),
  );
  return [first, ...rest].flatMap((p) => p.Items ?? []);
}

export type Component = {
  id: string;
  name: string;
  symbol: string | null;
  last: number | null;
  changePct: number | null;
  /** Percent of the index. */
  weight: number | null;
  /** Thousands of shekels. */
  turnover: number | null;
};

export async function index(id: string) {
  const oId = bareId(id);
  const ttl = await liveTtl();
  const d = await tase<RawIndexDetails | null>('api', `index/details?indexId=${oId}&lang=0`, ttl);
  if (!d || d.LastRate === null || d.LastRate === undefined || !d.Name) {
    throw new TaseError(`מדד ${oId} לא נמצא בבורסה`, 404);
  }
  const [quotes, weights] = await Promise.all([
    allPages<RawComponentQuote>('index/marketdata', oId, ttl),
    // Weights are rebalanced on a schedule, not during the day.
    allPages<RawComponentWeight>('index/components', oId, 6 * HOUR),
  ]);
  const weightOf = new Map(weights.map((w) => [bareId(w.SecurityNumber), num(w.Weight)]));
  const components: Component[] = quotes
    .map((q) => {
      const cid = bareId(q.ISIN_ID || q.Id);
      return {
        id: cid,
        name: tidy(q.Name),
        symbol: q.Symbol?.trim() || null,
        last: num(q.LastRate),
        changePct: num(q.Change),
        weight: weightOf.get(cid) ?? null,
        turnover: num(q.TurnOverValue),
      };
    })
    .sort((a, b) => (b.weight ?? 0) - (a.weight ?? 0));

  const row = indexRow({ ...d, Id: oId });
  const base = num(d.BaseRate);
  // Breadth is only on the all-indices list.
  const listed = (await allIndices()).find((i) => i.id === oId);
  return {
    ...row,
    base,
    change: moved(row.last, base),
    gainers: row.gainers ?? listed?.gainers ?? null,
    decliners: row.decliners ?? listed?.decliners ?? null,
    unchanged: row.unchanged ?? listed?.unchanged ?? null,
    open: num(d.OpenRate),
    high: num(d.HighRate),
    low: num(d.LowRate),
    monthYield: num(d.MonthYield),
    yearYield: num(d.AnnualYield),
    /** Millions of shekels. */
    marketCap: num(d.MarketValue),
    about: prose(d.Description),
    components,
  };
}

// ── quotes for a list (watchlist, portfolio) ────────────────────────────────

export type Ref = { kind: Kind; id: string };

/** "s629014,i142" → refs. Throws on anything that is not that. */
export function parseRefs(ids: string): Ref[] {
  const out: Ref[] = [];
  for (const part of ids.split(',').map((s) => s.trim()).filter(Boolean)) {
    const m = /^([si])(\d{1,9})$/.exec(part);
    if (!m) throw new TaseError(`מזהה לא תקין: ${part}`, 400);
    out.push({ kind: m[1] === 's' ? 'security' : 'index', id: bareId(m[2]) });
  }
  return out;
}

export async function quotes(refs: Ref[]) {
  const wantsIndex = refs.some((r) => r.kind === 'index');
  const indices = wantsIndex ? new Map((await allIndices()).map((i) => [i.id, i])) : new Map();

  const found: Quote[] = [];
  const missing: Array<Ref & { error: string }> = [];
  await Promise.all(
    refs.map(async (ref) => {
      if (ref.kind === 'index') {
        const row = indices.get(ref.id) as IndexRow | undefined;
        if (row) found.push(row);
        else missing.push({ ...ref, error: 'המדד לא נמצא' });
        return;
      }
      try {
        found.push(securityQuote(await rawSecurity(ref.id)));
      } catch (e) {
        // One delisted paper must not blank the whole portfolio.
        missing.push({ ...ref, error: e instanceof Error ? e.message : 'שגיאה' });
      }
    }),
  );
  const order = new Map(refs.map((r, i) => [r.kind + r.id, i]));
  found.sort((a, b) => (order.get(a.kind + a.id) ?? 0) - (order.get(b.kind + b.id) ?? 0));
  return { quotes: found, missing };
}

// ── charts ──────────────────────────────────────────────────────────────────

export const RANGES = ['1d', '1w', '1m', '3m', '6m', '1y', '3y', '5y'] as const;
export type Range = (typeof RANGES)[number];

/** TASE's `cp` period code for each range we offer (measured, not documented). */
const PERIOD: Record<Exclude<Range, '1d'>, number> = {
  '1w': 9,
  '1m': 1,
  '3m': 2,
  '6m': 3,
  '1y': 4,
  '3y': 6,
  '5y': 7,
};

type RawChart = {
  baseInfo: { dte?: string; brte?: number };
  inDay: Array<{ dtim: string; pval: number }>;
  history: Array<{ tdt: string; pval: number }>;
};

const MAX_POINTS = 240;

/** Keep at most `max` points, always including the first and the last. */
function thin<T>(points: T[], max: number): T[] {
  if (points.length <= max) return points;
  const step = (points.length - 1) / (max - 1);
  return Array.from({ length: max }, (_, i) => points[Math.round(i * step)]);
}

export async function chart(kind: Kind, id: string, range: Range) {
  const ot = kind === 'security' ? 1 : 2;
  const oid = kind === 'security' ? paddedId(id) : bareId(id);
  const query = (cp: number) =>
    `ct=1&ot=${ot}&lang=1&cf=0&cp=${cp}&cv=0&cl=0&cgt=1&dFrom=&dTo=&oid=${oid}`;

  if (range === '1d') {
    const raw = await tase<RawChart>(
      'micro',
      `chartdata/getindaydata?${query(0)}`,
      (await isOpen()) ? 30 * SEC : 10 * MIN,
    );
    const date = isoDate(raw.baseInfo?.dte);
    // Many trades share a minute; the last one is that minute's price.
    const perMinute = new Map<string, number>();
    for (const p of raw.inDay ?? []) {
      const t = clock(p.dtim);
      if (t) perMinute.set(t, p.pval);
    }
    const points = [...perMinute].map(([t, v]) => ({ t: date ? `${date}T${t}` : t, v }));
    return { range, base: num(raw.baseInfo?.brte), points: thin(points, MAX_POINTS) };
  }

  const raw = await tase<RawChart>(
    'micro',
    `chartdata/gethistorydata?${query(PERIOD[range])}`,
    (await isOpen()) ? 10 * MIN : HOUR,
  );
  const points = (raw.history ?? []).flatMap((p) => {
    const t = isoDate(p.tdt);
    return t ? [{ t, v: p.pval }] : [];
  });
  return { range, base: points[0]?.v ?? null, points: thin(points, MAX_POINTS) };
}
