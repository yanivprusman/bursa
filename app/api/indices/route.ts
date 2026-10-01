import { allIndices } from '@/lib/market';
import { respond } from '@/lib/respond';

export const GET = () => respond(async () => ({ indices: await allIndices() }));
