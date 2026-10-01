import type { NextRequest } from 'next/server';
import { respond } from '@/lib/respond';
import { search } from '@/lib/search';

export const GET = (req: NextRequest) =>
  respond(async () => ({ hits: await search(req.nextUrl.searchParams.get('q') ?? '') }));
