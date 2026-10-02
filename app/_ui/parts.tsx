'use client';

import { useState, type CSSProperties, type ReactNode } from 'react';
import { useFlash } from './hooks';
import * as F from './format';
import type { Kind } from './api';

/**
 * A string that is only a number: left-to-right and even-width digits, whatever the
 * page direction. Pass the underlying value as `flashOn` for a live price — when it
 * changes between two refreshes the number flashes green or red, as on a quote board.
 */
export function Num({ children, flashOn, className = '', style }: { children: ReactNode; flashOn?: number | null; className?: string; style?: CSSProperties }) {
  const flash = useFlash(flashOn);
  return (
    <span className={`num ${flash ? `flash-${flash}` : ''} ${className}`} style={style}>
      {children}
    </span>
  );
}

export const tone = (v: number | null | undefined) => (v == null || v === 0 ? 'flat' : v > 0 ? 'up' : 'down');

/** "▲ 0.34%" on a tinted pill. The arrow carries the direction for anyone who can't tell the colours apart. */
export function Chip({ pct, wide = false }: { pct: number | null | undefined; wide?: boolean }) {
  const text = pct == null ? '—' : pct > 0 ? `▲ ${F.fixed(Math.abs(pct), 2)}%` : pct < 0 ? `▼ ${F.fixed(Math.abs(pct), 2)}%` : '0.00%';
  return <span className={`chip ${tone(pct)} ${wide ? 'wide' : ''}`}>{text}</span>;
}

/** The candlestick mark: an index, or a company the exchange has no logo for. */
function Mark() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden className="mark">
      <rect x="6.1" y="5" width="1.8" height="14" rx="0.9" />
      <rect x="4" y="8" width="6" height="8" rx="1.6" />
      <rect x="16.1" y="3" width="1.8" height="14" rx="0.9" />
      <rect x="14" y="6" width="6" height="8" rx="1.6" />
    </svg>
  );
}

/** The paper's face in a list: its issuer's logo on a white tile, or the candlestick mark. */
export function Logo({ kind, companyId, size = 36 }: { kind: Kind; companyId: string | null | undefined; size?: number }) {
  // Remembered per company: a 404 for one must not blank the tile once it shows another.
  const [failed, setFailed] = useState<string | null>(null);
  const style = { width: size, height: size, borderRadius: Math.round(size * 0.28) };
  if (kind === 'index' || !companyId || failed === companyId) {
    return (
      <span className="logo logo-mark" style={style}>
        <Mark />
      </span>
    );
  }
  return (
    <span className="logo" style={style}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={`/api/logo/${companyId}`} alt="" loading="lazy" onError={() => setFailed(companyId)} />
    </span>
  );
}

/** A row of mutually exclusive choices: one track, the chosen one raised. */
export function Segmented<T extends string>({ id, options, value, onChange }: { id: string; options: Array<{ value: T; label: string }>; value: T; onChange: (v: T) => void }) {
  return (
    <div className="seg" role="tablist">
      {options.map((o) => {
        const on = o.value === value;
        return (
          <button
            key={o.value}
            type="button"
            role="tab"
            aria-selected={on}
            className={on ? 'on' : ''}
            data-id={`${id}-${o.value}`}
            {...(on ? { 'data-active-tab': o.label } : {})}
            onClick={() => onChange(o.value)}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

/** "המסחר פתוח · 14:27" / "המסחר סגור · 1.10.2026". The dot breathes while trading. */
export function Status({ open, tradeDate, tradeTime }: { open: boolean; tradeDate: string | null; tradeTime: string | null }) {
  const asOf = open ? tradeTime : F.date(tradeDate);
  return (
    <span className={`status ${open ? 'open' : ''}`}>
      <i />
      {open ? 'המסחר פתוח' : 'המסחר סגור'}
      {asOf ? <span className="num">{asOf}</span> : null}
    </span>
  );
}

/** A bare line: the shape of a series, no axes. */
export function Spark({ values, width = 84, height = 26 }: { values: number[]; width?: number; height?: number }) {
  if (values.length < 2) return <span style={{ width, height, display: 'inline-block' }} />;
  const min = Math.min(...values);
  const span = Math.max(...values) - min || 1;
  const points = values.map((v, i) => `${((i / (values.length - 1)) * width).toFixed(1)},${(height - 2 - ((v - min) / span) * (height - 4)).toFixed(1)}`).join(' ');
  return (
    <svg className="spark" width={width} height={height} viewBox={`0 0 ${width} ${height}`} aria-hidden>
      <polyline points={points} fill="none" strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}

export function Skeleton({ w, h, r = 8 }: { w: number | string; h: number; r?: number }) {
  return <span className="skel" style={{ width: w, height: h, borderRadius: r }} />;
}

export function SkeletonRows({ n }: { n: number }) {
  return (
    <div aria-hidden>
      {Array.from({ length: n }, (_, i) => (
        <div className="row" key={i}>
          <Skeleton w={36} h={36} r={10} />
          <span className="grow" style={{ display: 'grid', gap: 6 }}>
            <Skeleton w={110} h={11} />
            <Skeleton w={64} h={9} />
          </span>
          <Skeleton w={70} h={24} />
        </div>
      ))}
    </div>
  );
}

/** Where today's price sits between the day's low and high. Low on the left, like a number line. */
export function DayRange({ low, high, last, unit, toneOf }: { low: number; high: number; last: number; unit: string; toneOf: number | null }) {
  const at = high > low ? Math.min(1, Math.max(0, (last - low) / (high - low))) : 0.5;
  return (
    <div className="dayrange">
      <div className="label">טווח יומי</div>
      <div className={`track ${tone(toneOf)}`} dir="ltr">
        <span className="fill" style={{ width: `${at * 100}%` }} />
        <span className="knob" style={{ left: `${at * 100}%` }} />
      </div>
      <div className="ends" dir="ltr">
        <span className="num">{F.price(low, unit)}</span>
        <span className="num">{F.price(high, unit)}</span>
      </div>
    </div>
  );
}
