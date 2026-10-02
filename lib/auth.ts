import 'server-only';
import { createTokenAuth } from '@automatelinux/token-auth';

/**
 * The guard on the one private thing this server holds: the owner's watchlist and
 * holdings (`/api/list`). Market data stays open — it is what the exchange already
 * publishes. The phone sends BURSA_API_TOKEN as a bearer token; a browser signs in
 * once and holds a cookie derived from it.
 */
export const auth = createTokenAuth({ envVar: 'BURSA_API_TOKEN', scope: 'bursa' });
