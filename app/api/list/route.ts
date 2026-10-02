import { auth } from '@/lib/auth';
import { ListError, change, readList } from '@/lib/list';

export const dynamic = 'force-dynamic';

function refused(reason: string) {
  const unset = auth.notConfigured();
  return Response.json({ error: reason }, { status: unset ? 503 : 401 });
}

async function answer(fn: () => Promise<unknown>) {
  try {
    return Response.json(await fn(), { headers: { 'Cache-Control': 'no-store' } });
  } catch (e) {
    if (e instanceof ListError) return Response.json({ error: e.message }, { status: e.status });
    console.error('[bursa] list', e);
    return Response.json({ error: 'שגיאת שרת' }, { status: 500 });
  }
}

/** The owner's list: `{ rev, updatedAt, items[] }`. */
export async function GET(request: Request) {
  const no = auth.authorize(request);
  if (no) return refused(no);
  return answer(readList);
}

/** One operation — follow, unfollow, hold, clearHolding, import. Returns the list after it. */
export async function POST(request: Request) {
  const no = auth.authorize(request);
  if (no) return refused(no);
  let op: unknown;
  try {
    op = await request.json();
  } catch {
    return Response.json({ error: 'גוף הבקשה אינו JSON' }, { status: 400 });
  }
  return answer(() => change(op));
}
