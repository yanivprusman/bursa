import type { NextRequest } from 'next/server';
import { RANGES, chart, type Range } from '@/lib/market';
import { badRequest, respond } from '@/lib/respond';

// ?kind=security|index&id=629014&range=1d|1w|1m|3m|6m|1y|3y|5y
export const GET = (req: NextRequest) =>
  respond(async () => {
    const p = req.nextUrl.searchParams;
    const kind = p.get('kind');
    const id = p.get('id') ?? '';
    const range = p.get('range') ?? '';
    if (kind !== 'security' && kind !== 'index') badRequest('kind חייב להיות security או index');
    if (!/^\d{1,9}$/.test(id)) badRequest('מזהה לא תקין');
    if (!(RANGES as readonly string[]).includes(range)) badRequest(`range חייב להיות אחד מ: ${RANGES.join(', ')}`);
    return chart(kind as 'security' | 'index', id, range as Range);
  });
