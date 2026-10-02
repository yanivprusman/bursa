'use client';

import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import * as F from './format';
import type { Range } from './api';

type Point = { t: string; v: number };

const HEIGHT = 320;
// Room for the price axis on the right, where the newest price is, and the time axis below.
const PAD = { top: 16, right: 62, bottom: 28, left: 10 };

/** Round steps (1, 2, 2.5, 5 × a power of ten) that give about `count` gridlines. */
function niceTicks(lo: number, hi: number, count: number): number[] {
  const raw = (hi - lo) / count;
  if (!(raw > 0)) return [lo];
  const pow = Math.pow(10, Math.floor(Math.log10(raw)));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * pow).find((s) => s >= raw) ?? 10 * pow;
  const out: number[] = [];
  for (let v = Math.ceil(lo / step) * step; v <= hi + step * 1e-9; v += step) out.push(Number(v.toFixed(10)));
  return out;
}

function timeLabel(t: string, range: Range): string {
  if (range === '1d') return t.slice(11, 16);
  if (range === '1y' || range === '3y' || range === '5y') return F.monthYear(t);
  return F.dayMonth(t);
}

/**
 * The price chart. One line, a faint wash under it, hairline gridlines, and a crosshair
 * that snaps to the nearest point — move the pointer (or focus the chart and use the
 * arrow keys) to read any point. Time runs left to right whatever the page direction.
 */
export function PriceChart({ points, base, range, unit, dim }: { points: Point[]; base: number | null; range: Range; unit: string; dim: boolean }) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [hover, setHover] = useState<number | null>(null);

  // Measured before the first paint, so the chart never shows an empty frame first;
  // the observer then follows the pane as the window is resized.
  useLayoutEffect(() => {
    const el = box.current;
    if (!el) return;
    const measure = () => setWidth(Math.floor(el.getBoundingClientRect().width));
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const intraday = range === '1d';
  const n = points.length;

  const g = useMemo(() => {
    if (n < 2 || width < 120) return null;
    const values = points.map((p) => p.v);
    // The previous close belongs on a day chart even when the whole day traded away from it.
    const ref = intraday && base != null ? base : null;
    let lo = Math.min(...values, ref ?? Infinity);
    let hi = Math.max(...values, ref ?? -Infinity);
    const margin = (hi - lo || Math.abs(hi) * 0.01 || 1) * 0.08;
    lo -= margin;
    hi += margin;
    const plotW = width - PAD.left - PAD.right;
    const plotH = HEIGHT - PAD.top - PAD.bottom;
    const x = (i: number) => PAD.left + (plotW * i) / (n - 1);
    const y = (v: number) => PAD.top + plotH * (1 - (v - lo) / (hi - lo));
    const line = values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join('');
    const area = `${line}L${x(n - 1).toFixed(1)},${PAD.top + plotH}L${x(0).toFixed(1)},${PAD.top + plotH}Z`;
    const first = ref ?? values[0];
    const direction = values[n - 1] > first ? 'up' : values[n - 1] < first ? 'down' : 'flat';
    const xTicks = Array.from(new Set([0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(f * (n - 1)))));
    return { values, ref, x, y, line, area, direction, yTicks: niceTicks(lo, hi, 4), xTicks, plotW, plotH };
  }, [points, n, width, base, intraday]);

  const indexAt = (clientX: number) => {
    const el = box.current;
    if (!el || !g) return null;
    const px = clientX - el.getBoundingClientRect().left - PAD.left;
    return Math.min(n - 1, Math.max(0, Math.round((px / g.plotW) * (n - 1))));
  };

  const at = hover != null && hover < n ? hover : null;

  return (
    <div
      ref={box}
      className={`chart ${dim ? 'dim' : ''}`}
      dir="ltr"
      style={{ height: HEIGHT }}
      tabIndex={0}
      role="img"
      aria-label="גרף מחיר. מקשי החצים מזיזים את הסמן בין הנקודות."
      data-id="price-chart"
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
        <svg width={width} height={HEIGHT} className={`plot ${g.direction}`}>
          <defs>
            <linearGradient id="wash" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="currentColor" stopOpacity="0.16" />
              <stop offset="1" stopColor="currentColor" stopOpacity="0" />
            </linearGradient>
          </defs>
          {g.yTicks.map((v) => (
            <g key={v}>
              <line className="grid" x1={PAD.left} x2={PAD.left + g.plotW} y1={g.y(v)} y2={g.y(v)} />
              <text className="axis" x={PAD.left + g.plotW + 8} y={g.y(v)} dominantBaseline="middle">
                {F.price(v, unit)}
              </text>
            </g>
          ))}
          {g.xTicks.map((i, k) => (
            <text key={i} className="axis" x={g.x(i)} y={HEIGHT - 8} textAnchor={k === 0 ? 'start' : k === g.xTicks.length - 1 ? 'end' : 'middle'}>
              {timeLabel(points[i].t, range)}
            </text>
          ))}
          <path d={g.area} fill="url(#wash)" />
          {g.ref != null && (
            <>
              <line className="ref" x1={PAD.left} x2={PAD.left + g.plotW} y1={g.y(g.ref)} y2={g.y(g.ref)} />
              <text className="axis ref-label" x={PAD.left + 4} y={g.y(g.ref) - 6}>
                סגירה קודמת
              </text>
            </>
          )}
          <path d={g.line} className="line" />
          {at == null ? (
            // A dot on the newest point: this is where the price is now.
            <circle className="dot" cx={g.x(n - 1)} cy={g.y(g.values[n - 1])} r="4" />
          ) : (
            <>
              <line className="cross" x1={g.x(at)} x2={g.x(at)} y1={PAD.top} y2={PAD.top + g.plotH} />
              <circle className="dot" cx={g.x(at)} cy={g.y(g.values[at])} r="4.5" />
            </>
          )}
        </svg>
      )}
      {g && at != null && (
        <div
          className="tip"
          style={
            // Flip to the other side of the crosshair before it would run off the plot.
            g.x(at) > width / 2 ? { right: width - g.x(at) + 12, top: PAD.top } : { left: g.x(at) + 12, top: PAD.top }
          }
        >
          <strong className="num">{F.price(g.values[at], unit)}</strong>
          <span className="num">{range === '1d' ? points[at].t.slice(11, 16) : F.date(points[at].t)}</span>
        </div>
      )}
    </div>
  );
}
