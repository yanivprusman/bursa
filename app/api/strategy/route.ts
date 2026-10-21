import type { NextRequest } from 'next/server';
import { auth } from '@/lib/auth';
import { StoreError } from '@/lib/store';
import { TaseError } from '@/lib/tase';
import { changeConfig } from '@/lib/strategy/config';
import { strategyView } from '@/lib/strategy/view';

export const dynamic = 'force-dynamic';

function refused(reason: string) {
  return Response.json({ error: reason }, { status: auth.notConfigured() ? 503 : 401 });
}

async function answer(fn: () => Promise<unknown>) {
  try {
    return Response.json(await fn(), { headers: { 'Cache-Control': 'no-store' } });
  } catch (e) {
    if (e instanceof StoreError || e instanceof TaseError) return Response.json({ error: e.message }, { status: e.status });
    console.error('[bursa] strategy', e);
    return Response.json({ error: 'שגיאת שרת' }, { status: 500 });
  }
}

/** ?id=629014[&date=YYYY-MM-DD] — the strategy on one paper, one day (today by default). */
export async function GET(request: NextRequest) {
  const no = auth.authorize(request);
  if (no) return refused(no);
  const p = request.nextUrl.searchParams;
  return answer(() => strategyView(p.get('id') ?? '', p.get('date')));
}

/** `{op:"enable", on}`, `{op:"paper", id, on}`, `{op:"params", params}`. The trader picks it up within ~20 s. */
export async function POST(request: Request) {
  const no = auth.authorize(request);
  if (no) return refused(no);
  let op: unknown;
  try {
    op = await request.json();
  } catch {
    return Response.json({ error: 'גוף הבקשה אינו JSON' }, { status: 400 });
  }
  return answer(() => changeConfig(op));
}
