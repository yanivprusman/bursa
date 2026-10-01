// The one place that talks to the Tel Aviv Stock Exchange.
//
// TASE has no free public API (the licensed one is the paid "Data Hub"), so this
// reads the same JSON endpoints market.tase.co.il's own pages call. They are
// undocumented and can change without notice — every caller goes through here so
// a change is fixed in one file and the phone app never has to be reinstalled.
//
// Three rules keep us a polite client:
//   1. Everything is cached (TTL depends on whether the market is trading).
//   2. Identical requests in flight are shared, never duplicated.
//   3. At most MAX_PARALLEL requests are open at once.

const API_HOST = 'https://api.tase.co.il/api/';
const MICRO_HOST = 'https://marketapi.tase.co.il/api/v1/';

export type TaseHost = 'api' | 'micro';

const HEADERS: Record<string, string> = {
  'User-Agent':
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36',
  Accept: 'application/json, text/plain, */*',
  'Accept-Language': 'he-IL',
  Referer: 'https://market.tase.co.il/',
  Origin: 'https://market.tase.co.il',
};

const REQUEST_TIMEOUT_MS = 15_000;
const MAX_PARALLEL = 4;

/** An upstream failure, with the HTTP status this server should answer with. */
export class TaseError extends Error {
  constructor(
    message: string,
    readonly status: number = 502,
  ) {
    super(message);
    this.name = 'TaseError';
  }
}

// ── concurrency gate ────────────────────────────────────────────────────────

let active = 0;
const waiting: Array<() => void> = [];

async function withSlot<T>(fn: () => Promise<T>): Promise<T> {
  if (active >= MAX_PARALLEL) await new Promise<void>((resolve) => waiting.push(resolve));
  active++;
  try {
    return await fn();
  } finally {
    active--;
    waiting.shift()?.();
  }
}

// ── cache ───────────────────────────────────────────────────────────────────

type Entry = { at: number; value: unknown };
const cache = new Map<string, Entry>();
const inflight = new Map<string, Promise<unknown>>();
const MAX_ENTRIES = 2000;

function remember(key: string, value: unknown) {
  if (cache.size >= MAX_ENTRIES) {
    // Map iterates in insertion order, so the first keys are the oldest.
    let drop = Math.ceil(MAX_ENTRIES / 10);
    for (const k of cache.keys()) {
      cache.delete(k);
      if (--drop <= 0) break;
    }
  }
  cache.set(key, { at: Date.now(), value });
}

async function request(host: TaseHost, path: string, body: unknown | undefined): Promise<unknown> {
  const url = (host === 'api' ? API_HOST : MICRO_HOST) + path;
  let res: Response;
  try {
    res = await fetch(url, {
      method: body === undefined ? 'GET' : 'POST',
      headers: body === undefined ? HEADERS : { ...HEADERS, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      cache: 'no-store',
    });
  } catch (e) {
    const why = e instanceof Error && e.name === 'TimeoutError' ? 'לא ענה בזמן' : 'לא זמין';
    throw new TaseError(`אתר הבורסה ${why}`, 504);
  }
  const text = await res.text();
  // The WAF answers an unknown or blocked URL with an HTML "Request Rejected" page.
  if (!res.ok || text.trimStart().startsWith('<')) {
    throw new TaseError(`אתר הבורסה דחה את הבקשה (${res.status}) — ${path.split('?')[0]}`);
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new TaseError(`תשובה לא צפויה מאתר הבורסה — ${path.split('?')[0]}`);
  }
}

/**
 * Fetch a TASE endpoint as JSON, cached for `ttlMs`.
 * A POST body is part of the cache key.
 */
export async function tase<T>(
  host: TaseHost,
  path: string,
  ttlMs: number,
  body?: unknown,
): Promise<T> {
  const key = `${host}:${path}${body === undefined ? '' : ':' + JSON.stringify(body)}`;
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < ttlMs) return hit.value as T;

  const pending = inflight.get(key);
  if (pending) return pending as Promise<T>;

  const p = withSlot(() => request(host, path, body))
    .then((value) => {
      remember(key, value);
      return value;
    })
    .finally(() => inflight.delete(key));
  inflight.set(key, p);
  return p as Promise<T>;
}

// ── small parsers shared by every normaliser ────────────────────────────────

/** "01/10/2026" → "2026-10-01". Anything else → null. */
export function isoDate(d: string | null | undefined): string | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(d ?? '');
  return m ? `${m[3]}-${m[2]}-${m[1]}` : null;
}

/** "14:27" / "14:27:05" → "14:27". "סוף יום", "" and null → null (end of day). */
export function clock(t: string | null | undefined): string | null {
  const m = /^(\d{2}:\d{2})(:\d{2})?$/.exec((t ?? '').trim());
  return m ? m[1] : null;
}

/** TASE pads security numbers to 8 digits in some endpoints and not in others. */
export function bareId(id: string | number): string {
  return String(id).replace(/^0+(?=\d)/, '');
}

export function paddedId(id: string | number): string {
  return bareId(id).padStart(8, '0');
}

/** The exchange pads some names with runs of spaces ("דיסקונט      א"). */
export function tidy(s: string | null | undefined): string {
  return (s ?? '').replace(/\s+/g, ' ').trim();
}

/** Company blurbs arrive with no space after a comma ("בפיתוח,ייצור ושיווק"). */
export function prose(s: string | null | undefined): string | null {
  const t = tidy(s).replace(/,(?=[^\s\d])/g, ', ');
  return t || null;
}

export function num(v: unknown): number | null {
  if (v === null || v === undefined || v === '') return null;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}
