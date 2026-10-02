// The shapes the desktop app reads, taken straight from the server code that produces
// them — so a field renamed on the server is a type error here, not a blank on screen.

import type { chart, index, overview, quotes, security, IndexRow, Mover, Quote, Range } from '@/lib/market';
import type { Hit } from '@/lib/search';
import type { List, Tracked } from '@/lib/list';

export type Overview = Awaited<ReturnType<typeof overview>>;
export type SecurityDetail = Awaited<ReturnType<typeof security>>;
export type IndexDetail = Awaited<ReturnType<typeof index>>;
export type ChartData = Awaited<ReturnType<typeof chart>>;
export type Quotes = Awaited<ReturnType<typeof quotes>>;
export type { Hit, IndexRow, List, Mover, Quote, Range, Tracked };

export type Kind = 'security' | 'index';
export type Ref = { kind: Kind; id: string };

/** "s629014" / "i142": the id /api/quotes takes, and the `?p=` in the address bar. */
export const refKey = (r: Ref) => (r.kind === 'index' ? 'i' : 's') + r.id;

export function parseKey(key: string | null): Ref | null {
  const m = /^([si])(\d{1,9})$/.exec(key ?? '');
  return m ? { kind: m[1] === 'i' ? 'index' : 'security', id: m[2] } : null;
}

export class ApiError extends Error {
  readonly status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export async function getJson<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, { ...init, cache: 'no-store' });
  } catch {
    throw new ApiError('אין חיבור לשרת', 0);
  }
  const body = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError((body as { error?: string } | null)?.error || `שגיאת שרת ${res.status}`, res.status);
  return body as T;
}

export const postJson = <T,>(path: string, body: unknown) =>
  getJson<T>(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
