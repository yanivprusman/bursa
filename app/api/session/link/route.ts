import { auth } from '@/lib/auth';

export const dynamic = 'force-dynamic';

/**
 * Sign in from a link made by `scripts/make-link.mjs`: `?exp=<unix seconds>&sig=<hmac>`.
 * The link carries a signature over its own expiry and nothing else, so it is safe
 * to send to yourself and worthless once the minutes are up.
 */
export async function GET(request: Request) {
  const url = new URL(request.url);
  const exp = Number(url.searchParams.get('exp'));
  const sig = url.searchParams.get('sig') ?? '';
  const home = new URL('/', url);
  // Built from the Host header, not from `request.url`: behind `next dev --hostname 0.0.0.0`
  // the latter says 0.0.0.0, which is not where the browser is.
  const host = request.headers.get('host');
  if (host) home.host = host;

  const good = Number.isFinite(exp) && exp * 1000 > Date.now() && auth.isLinkSignature(exp, sig);
  if (!good) {
    home.searchParams.set('signin', 'expired');
    return Response.redirect(home, 303);
  }
  return new Response(null, {
    status: 303,
    headers: { Location: home.toString(), 'Set-Cookie': auth.sessionCookieHeader(request) },
  });
}
