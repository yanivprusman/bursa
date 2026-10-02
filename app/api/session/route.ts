import { auth } from '@/lib/auth';

export const dynamic = 'force-dynamic';

/** Is this browser signed in? The page asks so it knows whether to show the list or the sign-in form. */
export async function GET(request: Request) {
  return Response.json({ signedIn: auth.hasSession(request), configured: auth.notConfigured() === null });
}

/**
 * Sign a browser in. The access code IS `BURSA_API_TOKEN` — the secret the phone
 * app carries. A second, friendlier password would be a second thing that can open
 * the list, and a weaker one.
 */
export async function POST(request: Request) {
  const unset = auth.notConfigured();
  if (unset) return Response.json({ ok: false, error: unset }, { status: 503 });

  let body: Record<string, unknown>;
  try {
    body = await request.json();
  } catch {
    return Response.json({ ok: false, error: 'invalid_json' }, { status: 400 });
  }
  if (!auth.isAccessCode(String(body.code ?? '').trim())) {
    return Response.json({ ok: false, error: 'wrong_code' }, { status: 401 });
  }
  return Response.json({ ok: true }, { headers: { 'Set-Cookie': auth.sessionCookieHeader(request) } });
}

/** Sign this browser out. Needs no proof: it only ever removes access, from the browser that asked. */
export async function DELETE() {
  return Response.json({ ok: true }, { headers: { 'Set-Cookie': auth.clearSessionCookieHeader() } });
}
