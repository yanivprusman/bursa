import { TaseError } from './tase';

/** Run a handler and turn what it throws into `{ error }` with the right status. */
export async function respond(fn: () => Promise<unknown>): Promise<Response> {
  try {
    return Response.json(await fn(), { headers: { 'Cache-Control': 'no-store' } });
  } catch (e) {
    if (e instanceof TaseError) return Response.json({ error: e.message }, { status: e.status });
    console.error('[bursa]', e);
    return Response.json({ error: 'שגיאת שרת' }, { status: 500 });
  }
}

export function badRequest(message: string): never {
  throw new TaseError(message, 400);
}
