// A company's logo, as the exchange publishes it (80×80 JPEG on white).
//
// The phone asks this server rather than the exchange so that the app talks to one
// host only, and so a logo is fetched from the exchange once, not once per phone.

const SOURCE = 'https://mayafiles.tase.co.il/logos/he-IL/';
const DAY = 24 * 60 * 60 * 1000;

type Entry = { at: number; body: ArrayBuffer | null };
const cache = new Map<string, Entry>();

export async function GET(_req: Request, ctx: RouteContext<'/api/logo/[id]'>) {
  const { id } = await ctx.params;
  if (!/^\d{1,6}$/.test(id)) return Response.json({ error: 'מספר חברה לא תקין' }, { status: 400 });
  const key = id.padStart(6, '0');

  let hit = cache.get(key);
  if (!hit || Date.now() - hit.at > DAY) {
    let body: ArrayBuffer | null = null;
    try {
      const res = await fetch(`${SOURCE}${key}.jpg`, {
        headers: { Referer: 'https://market.tase.co.il/', 'User-Agent': 'Mozilla/5.0' },
        signal: AbortSignal.timeout(10_000),
        cache: 'no-store',
      });
      // Every real logo is a JPEG. A company without one is answered 200 with a generic
      // PNG placeholder (or an HTML error page) — neither is a logo.
      if (res.ok && (res.headers.get('content-type') ?? '').startsWith('image/jpeg')) body = await res.arrayBuffer();
    } catch {
      return Response.json({ error: 'אתר הבורסה לא זמין' }, { status: 504 });
    }
    hit = { at: Date.now(), body };
    cache.set(key, hit);
  }
  if (!hit.body) return Response.json({ error: 'אין לוגו לחברה הזאת' }, { status: 404 });
  return new Response(hit.body, { headers: { 'Content-Type': 'image/jpeg', 'Cache-Control': 'public, max-age=86400' } });
}
