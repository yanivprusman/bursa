import { auth } from '@/lib/auth';
import { operate, readAccount } from '@/lib/account';
import { tradeQuote } from '@/lib/market';
import { StoreError } from '@/lib/store';
import { TaseError } from '@/lib/tase';

export const dynamic = 'force-dynamic';

function refused(reason: string) {
  return Response.json({ error: reason }, { status: auth.notConfigured() ? 503 : 401 });
}

async function answer(fn: () => Promise<unknown>) {
  try {
    return Response.json(await fn(), { headers: { 'Cache-Control': 'no-store' } });
  } catch (e) {
    if (e instanceof StoreError || e instanceof TaseError) return Response.json({ error: e.message }, { status: e.status });
    console.error('[bursa] account', e);
    return Response.json({ error: 'שגיאת שרת' }, { status: 500 });
  }
}

/** The trading account, after filling whatever waiting order may now be filled. */
export async function GET(request: Request) {
  const no = auth.authorize(request);
  if (no) return refused(no);
  return answer(() => readAccount(tradeQuote));
}

/** One operation: `{op:"order", side, id, qty}`, `{op:"cancel", orderId}`, `{op:"reset", confirm:true}`. */
export async function POST(request: Request) {
  const no = auth.authorize(request);
  if (no) return refused(no);
  let op: unknown;
  try {
    op = await request.json();
  } catch {
    return Response.json({ error: 'גוף הבקשה אינו JSON' }, { status: 400 });
  }
  return answer(() => operate(op, tradeQuote));
}
