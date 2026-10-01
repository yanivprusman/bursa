import { index } from '@/lib/market';
import { badRequest, respond } from '@/lib/respond';

export const GET = (_req: Request, ctx: RouteContext<'/api/index/[id]'>) =>
  respond(async () => {
    const { id } = await ctx.params;
    if (!/^\d{1,9}$/.test(id)) badRequest('מספר מדד לא תקין');
    return index(id);
  });
