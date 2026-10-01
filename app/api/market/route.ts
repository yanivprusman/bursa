import { overview } from '@/lib/market';
import { respond } from '@/lib/respond';

export const GET = () => respond(overview);
