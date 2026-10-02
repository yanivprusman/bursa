// Number and date wording for the desktop app — the same rules as the phone's Fmt
// (mobile/shared/.../util/Format.kt), so a price reads the same on both.

function grouped(n: number, decimals: number): string {
  return Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

/** 1234567.891, 2 → "1,234,567.89". A value that rounds to zero never shows a minus. */
export function fixed(v: number, decimals: number): string {
  const body = grouped(v, decimals);
  const zero = !/[1-9]/.test(body);
  return (v < 0 && !zero ? '-' : '') + body;
}

/** Up to `max` decimals, trailing zeros dropped: 12160 → "12,160", 104.5 → "104.5". */
export function trimmed(v: number, max = 2): string {
  const s = fixed(v, max);
  return s.includes('.') ? s.replace(/0+$/, '').replace(/\.$/, '') : s;
}

/** A quoted price: indices always carry two decimals, securities only when they have them. */
export const price = (v: number, unit: string) => (unit === 'points' ? fixed(v, 2) : trimmed(v, 2));

const signed = (v: number, text: string) => (v > 0 && !text.startsWith('-') ? `+${text}` : text);

/** "+0.34%" / "-1.22%" / "0.00%". */
export const pct = (v: number) => signed(v, fixed(v, 2)) + '%';

/** "₪1,234.50"; from ₪10,000 up the agorot are noise and are dropped. */
export function shekels(v: number): string {
  const body = Math.abs(v) >= 10_000 ? fixed(Math.abs(v), 0) : fixed(Math.abs(v), 2);
  const zero = !/[1-9]/.test(body);
  return (v < 0 && !zero ? '-' : '') + '₪' + body;
}

export const signedShekels = (v: number) => signed(v, shekels(v));

/** A large shekel amount in words: 141_700_004_000 → "141.7 מיליארד ₪". */
export function bigShekels(v: number): string {
  const a = Math.abs(v);
  if (a >= 1e12) return trimmed(v / 1e12, 2) + ' טריליון ₪';
  if (a >= 1e9) return trimmed(v / 1e9, 1) + ' מיליארד ₪';
  if (a >= 1e6) return trimmed(v / 1e6, 1) + ' מיליון ₪';
  if (a >= 1e4) return trimmed(v / 1e3, 0) + ' אלף ₪';
  return fixed(v, 0) + ' ₪';
}

/** "2026-10-01" → "1.10.2026". */
export function date(iso: string | null | undefined): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '');
  return m ? `${Number(m[3])}.${Number(m[2])}.${m[1]}` : '';
}

/** "2026-10-01" → "1.10". */
export function dayMonth(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return m ? `${Number(m[3])}.${Number(m[2])}` : iso;
}

/** "2026-10-01" → "10/26". */
export function monthYear(iso: string): string {
  const m = /^(\d{4})-(\d{2})/.exec(iso);
  return m ? `${Number(m[2])}/${m[1].slice(2)}` : iso;
}

/** What a person typed into a number field: "1,250.5" works; empty, negative or text is null. */
export function parse(text: string): number | null {
  const clean = text.trim().replace(/,/g, '');
  if (!/^\d+(\.\d+)?$/.test(clean)) return null;
  return Number(clean);
}
