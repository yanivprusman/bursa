'use client';

import { useState } from 'react';
import type { ChartData, IndexDetail, Range, SecurityDetail, Tracked } from './api';
import { useResource, type ListState, type Selection } from './hooks';
import * as F from './format';
import { PriceChart } from './Chart';
import { Chip, DayRange, Logo, Num, Segmented, Skeleton, tone } from './parts';
import { holdingValue } from './portfolio';

/** Range key → the tab's label → how the move over that range is worded. */
const RANGES: Array<{ value: Range; label: string; phrase: string }> = [
  { value: '1d', label: 'יום', phrase: 'מול סגירה קודמת' },
  { value: '1w', label: 'שבוע', phrase: 'בשבוע האחרון' },
  { value: '1m', label: 'חודש', phrase: 'בחודש האחרון' },
  { value: '3m', label: '3 חודשים', phrase: 'ב-3 חודשים' },
  { value: '6m', label: 'חצי שנה', phrase: 'בחצי שנה' },
  { value: '1y', label: 'שנה', phrase: 'בשנה האחרונה' },
  { value: '3y', label: '3 שנים', phrase: 'ב-3 שנים' },
  { value: '5y', label: '5 שנים', phrase: 'ב-5 שנים' },
];

type Common = {
  sel: Selection;
  live: boolean;
  list: ListState;
  onPick: (s: Selection) => void;
  onEditHolding: (t: Tracked | (Selection & { symbol?: string | null; type?: string | null; companyId?: string | null })) => void;
};

/** The middle pane: everything about one paper. */
export function Detail(props: Common) {
  return props.sel.kind === 'index' ? <IndexView {...props} /> : <SecurityView {...props} />;
}

function Failed({ message, retry }: { message: string; retry: () => void }) {
  return (
    <div className="empty">
      <p>{message}</p>
      <button type="button" className="btn" data-id="retry-detail" onClick={retry}>
        נסו שוב
      </button>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="detail-skel" aria-hidden>
      <Skeleton w={220} h={26} />
      <Skeleton w={300} h={12} />
      <Skeleton w={200} h={46} />
      <Skeleton w="100%" h={380} r={16} />
      <Skeleton w="100%" h={160} r={16} />
    </div>
  );
}

/** The chart card: the move over the chosen range, the range tabs, the chart itself. */
function ChartCard({ sel, unit, live }: { sel: Selection; unit: string; live: boolean }) {
  const [range, setRange] = useState<Range>('1d');
  const path = `/api/chart?kind=${sel.kind}&id=${sel.id}&range=${range}`;
  const res = useResource<ChartData>(path, live && range === '1d');
  // While another range or paper loads, the previous line stays up, dimmed — no blank frame.
  const data = res.data;
  const shown = (data?.range ?? range) as Range;
  const points = data?.points ?? [];
  const reference = shown === '1d' ? data?.base : points[0]?.v;
  const last = points.at(-1)?.v;
  const move = reference != null && last != null && reference !== 0 ? ((last - reference) / reference) * 100 : null;
  const phrase = RANGES.find((r) => r.value === shown)?.phrase;

  return (
    <div className="card chart-card">
      <div className="chart-head">
        <div className="chart-move">
          {move != null && (
            <>
              <Chip pct={move} wide />
              <span className="muted">{phrase}</span>
            </>
          )}
        </div>
        <Segmented id="range" value={range} onChange={setRange} options={RANGES.map(({ value, label }) => ({ value, label }))} />
      </div>
      {points.length >= 2 ? (
        <PriceChart points={points} base={data?.base ?? null} range={shown} unit={unit} dim={res.dataPath !== path} />
      ) : res.error && !data ? (
        <Failed message={res.error} retry={res.refresh} />
      ) : !data || res.dataPath !== path ? (
        <Skeleton w="100%" h={320} r={12} />
      ) : (
        <div className="empty" style={{ height: 320 }}>
          <p>{range === '1d' ? 'עדיין אין עסקאות היום' : 'אין נתונים לתקופה הזאת'}</p>
        </div>
      )}
    </div>
  );
}

function Head({ sel, kind, companyId, sub, list, tracked, onFollow, children }: { sel: Selection; kind: 'security' | 'index'; companyId: string | null; sub: string; list: ListState; tracked: Tracked | undefined; onFollow: () => void; children?: React.ReactNode }) {
  return (
    <div className="d-head">
      <Logo kind={kind} companyId={companyId} size={52} />
      <div className="grow">
        <h1>{sel.name}</h1>
        <p className="sub">{sub}</p>
      </div>
      {list.signedIn && (
        <div className="d-actions">
          {children}
          {/* Un-following a held paper would delete the holding; that is done in the holding editor. */}
          {!(tracked?.qty && tracked.qty > 0) && (
            <button type="button" className={`btn ${tracked ? 'on' : ''}`} data-id="toggle-follow" onClick={onFollow}>
              {tracked ? '★ במעקב' : '☆ מעקב'}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function PriceLine({ last, unit, change, changePct, tradeDate, tradeTime }: { last: number; unit: string; change: number | null; changePct: number | null; tradeDate: string | null; tradeTime: string | null }) {
  return (
    <div className="d-price">
      <Num flashOn={last} className="price">
        {F.price(last, unit)}
      </Num>
      <span className="unit">{unit === 'points' ? "נק'" : "אג'"}</span>
      {changePct != null && (
        <Num className={`move ${tone(changePct)}`}>
          {changePct > 0 ? '▲ ' : changePct < 0 ? '▼ ' : ''}
          {change != null ? `${F.price(Math.abs(change), unit)}  ` : ''}({F.pct(changePct)})
        </Num>
      )}
      <span className="asof">
        {tradeTime ? (
          <>
            עסקה אחרונה <span className="num">{tradeTime}</span>
          </>
        ) : (
          <>
            סוף יום המסחר <span className="num">{F.date(tradeDate)}</span>
          </>
        )}
      </span>
    </div>
  );
}

function Fact({ label, value, toneOf, text = false }: { label: string; value: string; toneOf?: number | null; text?: boolean }) {
  return (
    <div className="fact">
      <span className="label">{label}</span>
      <span className={`value ${text ? '' : 'num'} ${toneOf === undefined ? '' : tone(toneOf)}`}>{value}</span>
    </div>
  );
}

function SecurityView({ sel, live, list, onEditHolding }: Common) {
  const path = `/api/security/${sel.id}`;
  const res = useResource<SecurityDetail>(path, live);
  const d = res.dataPath === path ? res.data : null;
  const tracked = list.find('security', sel.id);
  // What gets stored when the owner follows or holds this paper. The name it was opened
  // under wins: search calls an ETF by its full name, the quote by a clipped one.
  const self = { ...sel, name: sel.name || d?.name || '', symbol: d?.symbol, type: d?.type, companyId: d?.companyId };

  if (!d) return res.error ? <Failed message={res.error} retry={res.refresh} /> : <DetailSkeleton />;

  const held = tracked && (tracked.qty ?? 0) > 0 ? tracked : null;
  const hv = held ? holdingValue(held.qty ?? 0, held.avgCost, d.last, d.base) : null;

  return (
    <>
      <Head
        sel={{ ...sel, name: self.name }}
        kind="security"
        companyId={d.companyId}
        sub={[d.longName, d.subType ?? d.type, d.sector].filter(Boolean).join(' · ')}
        list={list}
        tracked={tracked}
        onFollow={() => (tracked ? list.unfollow(sel) : list.follow(self)).catch(() => undefined)}
      >
        <button type="button" className="btn primary" data-id="edit-holding" onClick={() => onEditHolding(tracked ?? self)}>
          {held ? 'עריכת ההחזקה' : 'הוספת החזקה'}
        </button>
      </Head>
      <PriceLine last={d.last} unit={d.unit} change={d.change} changePct={d.changePct} tradeDate={d.tradeDate} tradeTime={d.tradeTime} />
      {res.error && <div className="note bad">{res.error} — מוצגים הנתונים האחרונים</div>}
      <ChartCard sel={sel} unit={d.unit} live={live} />

      <div className="d-grid">
        <div className="card">
          <h2 className="card-title">
            נתוני מסחר <span className="muted num">{F.date(d.tradeDate)}</span>
          </h2>
          {d.low != null && d.high != null && d.high > d.low && <DayRange low={d.low} high={d.high} last={d.last} unit={d.unit} toneOf={d.changePct} />}
          <div className="facts">
            {d.open != null && <Fact label="שער פתיחה" value={F.price(d.open, d.unit)} />}
            {d.base != null && <Fact label="שער בסיס" value={F.price(d.base, d.unit)} />}
            {d.turnover != null && <Fact label="מחזור" value={F.bigShekels(d.turnover)} text />}
            {d.deals != null && <Fact label="עסקאות" value={F.fixed(d.deals, 0)} />}
            {/* Market cap arrives in thousands of shekels. */}
            {d.marketCap != null && d.marketCap > 0 && <Fact label="שווי שוק" value={F.bigShekels(d.marketCap * 1000)} text />}
            {d.monthYield != null && <Fact label="מתחילת החודש" value={F.pct(d.monthYield)} toneOf={d.monthYield} />}
            {d.yearYield != null && <Fact label="מתחילת השנה" value={F.pct(d.yearYield)} toneOf={d.yearYield} />}
            {d.grossYield != null && <Fact label="תשואה ברוטו לפדיון" value={F.pct(d.grossYield)} toneOf={d.grossYield} />}
            {d.annualInterest != null && <Fact label="ריבית שנתית" value={`${F.trimmed(d.annualInterest, 3)}%`} />}
            {d.redemptionDate && <Fact label="מועד פדיון" value={F.date(d.redemptionDate)} />}
            {/* "לא צמוד" on a share says nothing; linkage matters for a bond. */}
            {(d.redemptionDate || d.grossYield != null) && d.linkage && <Fact label="הצמדה" value={d.linkage} text />}
            <Fact label="מספר נייר" value={d.id} />
            {d.isin && <Fact label="ISIN" value={d.isin} />}
          </div>
        </div>

        <div className="stack">
          {hv && held && (
            <div className="card">
              <h2 className="card-title">ההחזקה שלי</h2>
              <Num flashOn={hv.value} className="holding-value">
                {F.shekels(hv.value)}
              </Num>
              <div className="muted">
                <Num>
                  {F.trimmed(held.qty ?? 0, 4)} × {F.trimmed(d.last)}
                </Num>
              </div>
              <div className="facts">
                {hv.dayChange != null && <Fact label="היום" value={F.signedShekels(hv.dayChange)} toneOf={hv.dayChange} />}
                {hv.gain != null && <Fact label="מאז הקנייה" value={`${F.signedShekels(hv.gain)}${hv.gainPct != null ? `  (${F.pct(hv.gainPct)})` : ''}`} toneOf={hv.gain} />}
              </div>
            </div>
          )}
          {d.about && (
            <div className="card">
              <h2 className="card-title">על החברה</h2>
              <p className="about">{d.about}</p>
              {d.site && (
                <a className="link num" data-id="company-site" href={d.site.startsWith('http') ? d.site : `https://${d.site}`} target="_blank" rel="noreferrer">
                  {d.site}
                </a>
              )}
            </div>
          )}
        </div>
      </div>
    </>
  );
}

type Order = 'weight' | 'up' | 'down';

function IndexView({ sel, live, list, onPick }: Common) {
  const path = `/api/index/${sel.id}`;
  const res = useResource<IndexDetail>(path, live);
  const d = res.dataPath === path ? res.data : null;
  const [order, setOrder] = useState<Order>('weight');
  const tracked = list.find('index', sel.id);

  if (!d) return res.error ? <Failed message={res.error} retry={res.refresh} /> : <DetailSkeleton />;

  const self = { ...sel, name: sel.name || d.name, type: 'מדד' };
  const components =
    order === 'weight' ? d.components : [...d.components].sort((a, b) => (order === 'up' ? (b.changePct ?? -Infinity) - (a.changePct ?? -Infinity) : (a.changePct ?? Infinity) - (b.changePct ?? Infinity)));

  return (
    <>
      <Head sel={{ ...sel, name: self.name }} kind="index" companyId={null} sub={d.category} list={list} tracked={tracked} onFollow={() => (tracked ? list.unfollow(sel) : list.follow(self)).catch(() => undefined)} />
      <PriceLine last={d.last} unit={d.unit} change={d.change} changePct={d.changePct} tradeDate={d.tradeDate} tradeTime={d.tradeTime} />
      {res.error && <div className="note bad">{res.error} — מוצגים הנתונים האחרונים</div>}
      <ChartCard sel={sel} unit={d.unit} live={live} />

      <div className="card">
        <h2 className="card-title">
          נתוני המדד <span className="muted num">{F.date(d.tradeDate)}</span>
        </h2>
        {d.low != null && d.high != null && d.high > d.low && <DayRange low={d.low} high={d.high} last={d.last} unit={d.unit} toneOf={d.changePct} />}
        <div className="facts wide">
          {d.open != null && <Fact label="פתיחה" value={F.fixed(d.open, 2)} />}
          {d.base != null && <Fact label="בסיס" value={F.fixed(d.base, 2)} />}
          {d.monthYield != null && <Fact label="מתחילת החודש" value={F.pct(d.monthYield)} toneOf={d.monthYield} />}
          {d.yearYield != null && <Fact label="מתחילת השנה" value={F.pct(d.yearYield)} toneOf={d.yearYield} />}
          {d.gainers != null && <Fact label="עלו היום" value={String(d.gainers)} toneOf={1} />}
          {d.decliners != null && <Fact label="ירדו היום" value={String(d.decliners)} toneOf={-1} />}
          {/* Index market cap arrives in millions of shekels. */}
          {d.marketCap != null && d.marketCap > 0 && <Fact label="שווי שוק" value={F.bigShekels(d.marketCap * 1e6)} text />}
        </div>
        {d.about && <p className="about spaced">{d.about}</p>}
      </div>

      {d.components.length > 0 && (
        <div className="card">
          <div className="card-head">
            <h2 className="card-title">{d.components.length} ניירות במדד</h2>
            <Segmented
              id="order"
              value={order}
              onChange={setOrder}
              options={[
                { value: 'weight', label: 'לפי משקל' },
                { value: 'up', label: 'העולות' },
                { value: 'down', label: 'היורדות' },
              ]}
            />
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>נייר</th>
                <th className="n">משקל במדד</th>
                <th className="n">שער</th>
                <th className="n">מחזור</th>
                <th className="n">שינוי</th>
              </tr>
            </thead>
            <tbody>
              {components.map((c) => (
                <tr key={c.id} data-id={`component-${c.id}`} tabIndex={0} onClick={() => onPick({ kind: 'security', id: c.id, name: c.name })} onKeyDown={(e) => e.key === 'Enter' && onPick({ kind: 'security', id: c.id, name: c.name })}>
                  <td>
                    <span className="cell-name">
                      <Logo kind="security" companyId={c.companyId} size={28} />
                      {c.name}
                    </span>
                  </td>
                  <td className="n num">{c.weight != null ? `${F.fixed(c.weight, 2)}%` : ''}</td>
                  <td className="n">{c.last != null && <Num flashOn={c.last}>{F.trimmed(c.last)}</Num>}</td>
                  {/* Component turnover arrives in thousands of shekels. */}
                  <td className="n muted">{c.turnover != null ? F.bigShekels(c.turnover * 1000) : ''}</td>
                  <td className="n">
                    <Chip pct={c.changePct} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
