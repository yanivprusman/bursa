import { overview, type Mover } from '@/lib/market';
import { TaseError } from '@/lib/tase';

// The product is the phone app. This page is its server's face: the same market
// snapshot the app's "שוק" tab shows, so anyone on the network can see at a
// glance that the data is flowing.

export const dynamic = 'force-dynamic';

const nf = (decimals: number) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });

function Chip({ pct }: { pct: number | null }) {
  if (pct === null) return <span className="chip">—</span>;
  const cls = pct > 0 ? 'chip up' : pct < 0 ? 'chip down' : 'chip';
  const arrow = pct > 0 ? '▲ ' : pct < 0 ? '▼ ' : '';
  return <span className={cls}>{arrow + nf(2).format(Math.abs(pct))}%</span>;
}

function Spark({ values }: { values: number[] }) {
  if (values.length < 2) return null;
  const min = Math.min(...values);
  const span = Math.max(...values) - min || 1;
  const points = values
    .map((v, i) => `${((i / (values.length - 1)) * 100).toFixed(2)},${(30 - ((v - min) / span) * 28).toFixed(2)}`)
    .join(' ');
  return (
    <svg className="spark" viewBox="0 0 100 32" preserveAspectRatio="none" aria-hidden>
      <polyline points={points} fill="none" stroke="var(--gold)" strokeWidth="1.6" vectorEffect="non-scaling-stroke" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}

function Movers({ title, rows }: { title: string; rows: Mover[] }) {
  return (
    <div className="card list">
      <h3>{title}</h3>
      {rows.map((m) => (
        <div className="row" key={m.id}>
          <span className="name">{m.name}</span>
          <span className="num">{nf(0).format(m.last)}</span>
          <Chip pct={m.changePct} />
        </div>
      ))}
    </div>
  );
}

function date(iso: string | null) {
  if (!iso) return '';
  const [y, m, d] = iso.split('-');
  return `${Number(d)}.${Number(m)}.${y}`;
}

export default async function Home() {
  let market: Awaited<ReturnType<typeof overview>> | null = null;
  let failure: string | null = null;
  try {
    market = await overview();
  } catch (e) {
    if (!(e instanceof TaseError)) throw e;
    failure = e.message;
  }

  return (
    <main className="board">
      <header className="board-head">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/icon.svg" alt="" />
        <h1>בורסה</h1>
        {market && (
          <span className={market.open ? 'status open' : 'status'}>
            <i />
            {market.open ? 'המסחר פתוח' : 'המסחר סגור'}
            <span className="num">{market.open ? market.tradeTime : date(market.tradeDate)}</span>
          </span>
        )}
      </header>
      <p className="lede">
        הבורסה לניירות ערך בתל אביב, בטלפון: מדדים, מניות, קרנות סל ואג&quot;ח, רשימת מעקב ותיק אישי.
        הדף הזה הוא לוח המצב של השרת שמזין את האפליקציה.
      </p>

      {failure && <div className="fail">{failure}</div>}

      {market && (
        <>
          <h2 className="section">מדדים</h2>
          <div className="cards">
            {market.indices.map((i) => (
              <div className="card" key={i.id}>
                <h3>{i.name}</h3>
                <div className="num last">{nf(2).format(i.last)}</div>
                <div style={{ marginTop: 8 }}>
                  <Chip pct={i.changePct} />
                </div>
                <Spark values={i.spark} />
              </div>
            ))}
          </div>

          <h2 className="section">בולטות בת&quot;א-125</h2>
          <div className="cols">
            <Movers title="עולות" rows={market.movers.gainers} />
            <Movers title="יורדות" rows={market.movers.losers} />
            <Movers title="מחזור גבוה" rows={market.movers.active} />
          </div>
        </>
      )}

      <p className="foot">
        הנתונים מאתר הבורסה לניירות ערך בתל אביב. מחירי ניירות הערך באגורות, המדדים בנקודות.
        <br />
        לממשק המלא ראו{' '}
        <a data-id="api-contract" href="https://github.com/yanivprusman/bursa/blob/main/API.md">
          API.md
        </a>
        .
      </p>
    </main>
  );
}
