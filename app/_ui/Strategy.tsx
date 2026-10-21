'use client';

import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { StrategyView } from '@/lib/strategy/view';
import { postJson } from './api';
import { useResource } from './hooks';
import * as F from './format';
import { Num, Segmented, Skeleton, tone } from './parts';

type Bar = StrategyView['bars'][number];
type Mark = { i: number; side: 'buy' | 'sell'; done: boolean };

const REASON: Record<string, string> = {
  'cross-up': 'EMA חצה את VWAP כלפי מעלה',
  stop: 'עצירת הפסד',
  'below-ema': 'נר נסגר מתחת ל-EMA',
  'end-of-day': 'סוף יום',
};

const HEIGHT = 300;
const PAD = { top: 14, right: 62, bottom: 26, left: 8 };

/**
 * The bars the strategy decides on: candles, the session VWAP (gold) and the EMA (blue),
 * and its decisions — hollow where the rules say it would have traded, solid where the
 * trader actually did. A bar still forming is drawn faint and has no decision yet.
 */
function CandleChart({ bars, closed, marks }: { bars: Bar[]; closed: number; marks: Mark[] }) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [hover, setHover] = useState<number | null>(null);

  useLayoutEffect(() => {
    const el = box.current;
    if (!el) return;
    const measure = () => setWidth(Math.floor(el.getBoundingClientRect().width));
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const n = bars.length;
  const g = useMemo(() => {
    if (n < 1 || width < 120) return null;
    const vals = bars.flatMap((b) => [b.h, b.l, b.ema, ...(b.vwap != null ? [b.vwap] : [])]);
    let lo = Math.min(...vals);
    let hi = Math.max(...vals);
    const margin = (hi - lo || hi * 0.01 || 1) * 0.1;
    lo -= margin;
    hi += margin;
    const plotW = width - PAD.left - PAD.right;
    const plotH = HEIGHT - PAD.top - PAD.bottom;
    const step = plotW / Math.max(n, 1);
    const x = (i: number) => PAD.left + step * (i + 0.5);
    const y = (v: number) => PAD.top + plotH * (1 - (v - lo) / (hi - lo));
    const path = (pick: (b: Bar) => number | null) => {
      let d = '';
      bars.forEach((b, i) => {
        const v = pick(b);
        if (v == null) return;
        d += `${d ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`;
      });
      return d;
    };
    const ticks: number[] = [];
    const span = hi - lo;
    const raw = span / 4;
    const pow = Math.pow(10, Math.floor(Math.log10(raw)));
    const unit = [1, 2, 2.5, 5, 10].map((m) => m * pow).find((s) => s >= raw) ?? 10 * pow;
    for (let v = Math.ceil(lo / unit) * unit; v <= hi; v += unit) ticks.push(Number(v.toFixed(8)));
    const xTicks = Array.from(new Set([0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(f * (n - 1)))));
    return { x, y, step, plotW, plotH, vwap: path((b) => b.vwap), ema: path((b) => b.ema), ticks, xTicks };
  }, [bars, n, width]);

  const indexAt = (clientX: number) => {
    const el = box.current;
    if (!el || !g) return null;
    const px = clientX - el.getBoundingClientRect().left - PAD.left;
    return Math.min(n - 1, Math.max(0, Math.floor(px / g.step)));
  };
  const at = hover != null && hover < n ? bars[hover] : null;
  const body = g ? Math.max(1, Math.min(10, g.step * 0.62)) : 0;

  return (
    <div
      ref={box}
      className="chart"
      dir="ltr"
      style={{ height: HEIGHT }}
      tabIndex={0}
      role="img"
      aria-label="גרף נרות של 3 דקות עם VWAP ו-EMA. מקשי החצים מזיזים את הסמן."
      data-id="strategy-chart"
      onPointerMove={(e) => setHover(indexAt(e.clientX))}
      onPointerLeave={() => setHover(null)}
      onBlur={() => setHover(null)}
      onKeyDown={(e) => {
        if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
        e.preventDefault();
        setHover((h) => Math.min(n - 1, Math.max(0, (h ?? n - 1) + (e.key === 'ArrowRight' ? 1 : -1))));
      }}
    >
      {g && (
        <svg width={width} height={HEIGHT} className="plot candles">
          {g.ticks.map((v) => (
            <g key={v}>
              <line className="grid" x1={PAD.left} x2={PAD.left + g.plotW} y1={g.y(v)} y2={g.y(v)} />
              <text className="axis" x={PAD.left + g.plotW + 8} y={g.y(v)} dominantBaseline="middle">
                {F.trimmed(v)}
              </text>
            </g>
          ))}
          {g.xTicks.map((i, k) => (
            <text key={i} className="axis" x={g.x(i)} y={HEIGHT - 7} textAnchor={k === 0 ? 'start' : k === g.xTicks.length - 1 ? 'end' : 'middle'}>
              {bars[i].t}
            </text>
          ))}
          {bars.map((b, i) => {
            const dir = b.c > b.o ? 'up' : b.c < b.o ? 'down' : 'flat';
            const top = g.y(Math.max(b.o, b.c));
            return (
              <g key={b.t} className={`candle ${dir} ${i >= closed ? 'forming' : ''}`}>
                <line x1={g.x(i)} x2={g.x(i)} y1={g.y(b.h)} y2={g.y(b.l)} />
                <rect x={g.x(i) - body / 2} y={top} width={body} height={Math.max(1, g.y(Math.min(b.o, b.c)) - top)} />
              </g>
            );
          })}
          <path d={g.vwap} className="series vwap" />
          <path d={g.ema} className="series ema" />
          {marks.map((m, k) => {
            const b = bars[m.i];
            const cx = g.x(m.i);
            const cy = m.side === 'buy' ? g.y(b.l) + 12 : g.y(b.h) - 12;
            const d = m.side === 'buy' ? `M${cx},${cy - 6}L${cx - 6},${cy + 5}L${cx + 6},${cy + 5}Z` : `M${cx},${cy + 6}L${cx - 6},${cy - 5}L${cx + 6},${cy - 5}Z`;
            return <path key={k} d={d} className={`mark ${m.side} ${m.done ? 'done' : 'sim'}`} />;
          })}
          {hover != null && <line className="cross" x1={g.x(hover)} x2={g.x(hover)} y1={PAD.top} y2={PAD.top + g.plotH} />}
        </svg>
      )}
      {g && at && hover != null && (
        <div className="tip" style={g.x(hover) > width / 2 ? { right: width - g.x(hover) + 12, top: PAD.top } : { left: g.x(hover) + 12, top: PAD.top }}>
          <strong className="num">
            {at.t}–{at.end}
            {hover >= closed ? ' · בתהליך' : ''}
          </strong>
          <span className="num">
            פ {F.trimmed(at.o)} · ג {F.trimmed(at.h)} · נ {F.trimmed(at.l)} · ס {F.trimmed(at.c)}
          </span>
          <span className="num">
            <i className="key vwap" /> VWAP {at.vwap != null ? F.trimmed(at.vwap) : '—'} · <i className="key ema" /> EMA {F.trimmed(at.ema)}
          </span>
        </div>
      )}
    </div>
  );
}

function dayLabel(d: string, today: string) {
  return d === today ? 'היום' : `${Number(d.slice(8, 10))}.${Number(d.slice(5, 7))}`;
}

/** The strategy on this paper: switch it on or off, see its bars and what it did. */
export function StrategyCard({ id, live }: { id: string; live: boolean }) {
  const [date, setDate] = useState<string | null>(null);
  const path = `/api/strategy?id=${id}${date ? `&date=${date}` : ''}`;
  const res = useResource<StrategyView>(path, live && date === null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const v = res.dataPath === path ? res.data : null;

  const send = async (ops: unknown[]) => {
    setBusy(true);
    setError(null);
    try {
      for (const op of ops) await postJson('/api/strategy', op);
      res.refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  if (!v) {
    return (
      <div className="card strategy">
        {res.error ? <p className="note bad">{res.error}</p> : <Skeleton w="100%" h={360} r={12} />}
      </div>
    );
  }

  const active = v.enabled && v.following;
  const days = v.days.slice(-5);
  const index = new Map(v.bars.map((b, i) => [b.t, i]));
  const marks: Mark[] = [
    ...v.replay.flatMap((s) => (index.has(s.t) ? [{ i: index.get(s.t)!, side: s.side, done: false }] : [])),
    ...v.events.flatMap((e) => (e.price != null && index.has(e.bar) ? [{ i: index.get(e.bar)!, side: e.side, done: true }] : [])),
  ];
  const o = v.outcome;

  return (
    <div className="card strategy">
      <div className="card-head">
        <h2 className="card-title">
          אסטרטגיה: EMA-9 חוצה VWAP <span className="practice-tag">תרגול</span>
        </h2>
        <div className="d-actions">
          {v.enabled && v.papers.length > 0 && (
            <button type="button" className="btn" data-id="strategy-pause-all" disabled={busy} onClick={() => send([{ op: 'enable', on: false }])}>
              השהיית הכול
            </button>
          )}
          <button
            type="button"
            className={`btn ${active ? 'on' : 'primary'}`}
            data-id="strategy-toggle-paper"
            disabled={busy}
            onClick={() => send(active ? [{ op: 'paper', id, on: false }] : [{ op: 'paper', id, on: true }, { op: 'enable', on: true }])}
          >
            {active ? '● מסחר אוטומטי פועל' : 'הפעלת מסחר אוטומטי'}
          </button>
        </div>
      </div>

      <p className="muted strategy-rules">
        נרות של {v.params.barMinutes} דקות. <b>קנייה</b> כש-EMA-{v.params.emaPeriod} חוצה את VWAP כלפי מעלה; <b>מכירה</b> כשנר נסגר מתחת ל-EMA, בירידה של{' '}
        <span className="num">{v.params.stopPct}%</span> ממחיר הקנייה, או ב-<span className="num">{v.session.flatAt}</span>. כל עסקה{' '}
        <span className="num">{F.shekels(v.params.budget / 100)}</span>. רק קנייה — בחשבון אין מכירה בחסר.
      </p>

      {error && <p className="note bad">{error}</p>}
      {v.following && !v.enabled && <p className="note">המסחר האוטומטי מושהה לכל הניירות.</p>}
      {v.papers.length > 0 && !v.trader.alive && (
        <p className="note bad">שירות המסחר האוטומטי לא פועל{v.trader.at ? ` (נראה לאחרונה ${new Date(v.trader.at).toLocaleTimeString('he-IL')})` : ''} — לא יבוצעו עסקאות.</p>
      )}

      <div className="chart-head">
        <div className="legend">
          <span>
            <i className="key vwap" /> VWAP
          </span>
          <span>
            <i className="key ema" /> EMA-{v.params.emaPeriod}
          </span>
          <span>
            <i className="tri sim" /> לפי הכללים
          </span>
          <span>
            <i className="tri done" /> בוצע
          </span>
        </div>
        {days.length > 1 && (
          <Segmented id="strategy-day" value={date ?? v.today} onChange={(d) => setDate(d === v.today ? null : d)} options={days.map((d) => ({ value: d, label: dayLabel(d, v.today) }))} />
        )}
      </div>

      {v.bars.length > 0 ? (
        <CandleChart bars={v.bars} closed={v.closed} marks={marks} />
      ) : (
        <div className="empty" style={{ height: 300 }}>
          <p>{v.date === v.today ? 'עדיין אין עסקאות היום' : 'לא הוקלטו עסקאות ביום הזה'}</p>
        </div>
      )}

      <div className="strategy-foot">
        <div className="facts">
          <div className="fact">
            <span className="label">לפי הכללים, {dayLabel(v.date, v.today) === 'היום' ? 'היום' : `ב-${dayLabel(v.date, v.today)}`}</span>
            <span className="value">
              {o.trips === 0 ? (
                'אין עסקאות'
              ) : (
                <Num className={tone(o.pnl)}>
                  {F.signedShekels(o.pnl / 100)} ({F.pct(o.pct)})
                </Num>
              )}
            </span>
          </div>
          {o.trips > 0 && (
            <div className="fact">
              <span className="label">סבבים</span>
              <span className="value num">
                {o.trips} · {o.wins} ברווח{o.open ? ' · אחד עדיין פתוח' : ''}
              </span>
            </div>
          )}
          <div className="fact">
            <span className="label">ימים מוקלטים</span>
            <span className="value num">{v.days.length}</span>
          </div>
        </div>

        {(v.events.length > 0 || v.replay.length > 0) && (
          <div className="mini-trades">
            {(v.events.length > 0 ? v.events : []).map((e) => (
              <div key={e.signal} className="mini-trade">
                <span className={`side ${e.side}`}>{e.side === 'buy' ? 'קנייה' : 'מכירה'}</span>
                <Num>{e.bar}</Num>
                <span className="muted grow">{REASON[e.reason]}</span>
                {e.error ? <span className="bad-text">{e.error}</span> : <Num>{`${F.trimmed(e.qty, 0)} × ${e.price != null ? F.trimmed(e.price) : '—'}`}</Num>}
              </div>
            ))}
            {v.events.length === 0 &&
              v.replay.map((s) => (
                <div key={`${s.t}-${s.side}`} className="mini-trade sim">
                  <span className={`side ${s.side}`}>{s.side === 'buy' ? 'קנייה' : 'מכירה'}</span>
                  <Num>{s.t}</Num>
                  <span className="muted grow">{REASON[s.reason]}</span>
                  <Num>{F.trimmed(s.price)}</Num>
                </div>
              ))}
          </div>
        )}
        <p className="muted small">
          {v.events.length > 0 ? 'עסקאות שהשירות ביצע בפועל בחשבון התרגול.' : 'מה הכללים היו עושים — סימולציה בשער סגירת הנר, כולל עמלות.'} הבדיקה אמינה רק אחרי
          שמצטברים ימים מוקלטים; הבורסה שומרת עסקאות של יום אחד בלבד.
        </p>
      </div>
    </div>
  );
}
