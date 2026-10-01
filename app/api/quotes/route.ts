import type { NextRequest } from 'next/server';
import { parseRefs, quotes } from '@/lib/market';
import { badRequest, respond } from '@/lib/respond';

// ?ids=s629014,i142 — s = security, i = index.
const MAX_IDS = 80;

export const GET = (req: NextRequest) =>
  respond(async () => {
    const refs = parseRefs(req.nextUrl.searchParams.get('ids') ?? '');
    if (refs.length > MAX_IDS) badRequest(`עד ${MAX_IDS} ניירות בבקשה אחת`);
    return quotes(refs);
  });
