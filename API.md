# bursa API

The server behind the phone app and the desktop page.

- **Market data** — every route below except `/api/list` and `/api/session` — is
  open and read-only: it is what the exchange already publishes.
- **The owner's list** (`/api/list`) is the one private thing here and the one
  thing clients write. It needs `Authorization: Bearer <BURSA_API_TOKEN>` (the
  phone) or the session cookie a browser gets by signing in.

A failure is `{ "error": "<message>" }` with a 4xx/5xx status.

## Units

| What | Unit |
| :--- | :--- |
| `last`, `base`, `open`, `high`, `low`, `change` of a **security** | agorot (`unit: "agorot"`) |
| the same fields of an **index** | points (`unit: "points"`) |
| `changePct`, `monthYield`, `yearYield`, `weight` | percent |
| `turnover` on a security | shekels |
| `turnover` on an index, a mover or a component; `marketCap` of a security | thousands of shekels |
| `marketCap` of an index | millions of shekels |
| `tradeDate` | `YYYY-MM-DD` |
| `tradeTime` | `HH:MM` while the session runs, `null` once the day is settled |

`monthYield` / `yearYield` are **since the start of the calendar month / year**,
not trailing 30 / 365 days (that is how the exchange reports them).

## Routes

| Route | Returns |
| :--- | :--- |
| `/api/market` | `{ open, tradeDate, tradeTime, indices[], tape[], movers{gainers,losers,active}, breadth, turnovers[] }` — the headline indices carry `spark`, a month of closes; `tape` is the ticker strip |
| `/api/indices` | `{ indices[] }` — every index, with `category` |
| `/api/index/<id>` | one index plus `components[]` (sorted by weight) |
| `/api/security/<id>` | one security: quote, day range, yields, bond terms, `about` |
| `/api/quotes?ids=s629014,i142` | `{ quotes[], missing[] }` — `s` = security, `i` = index, up to 80 |
| `/api/chart?kind=security\|index&id=<id>&range=<r>` | `{ range, base, points[{t,v}] }`; `r` ∈ `1d 1w 1m 3m 6m 1y 3y 5y`, at most 240 points |
| `/api/search?q=<text>` | `{ hits[] }` — Hebrew or English name, symbol, ISIN or number |
| `/api/logo/<companyId>` | the issuer's logo, an 80×80 JPEG on white; `404` when the exchange has none. Securities, movers, components and search hits carry `companyId` |

An index in a list (`/api/market`, `/api/indices`, `/api/quotes`) has `base` and
`change` = `null`: the exchange's list carries only the percentage, and a previous
close derived from a rounded percentage would be wrong in the second decimal.
`/api/index/<id>` has the real one.

## The list

`GET /api/list` → `{ rev, updatedAt, items[] }`. An item is
`{ kind, id, name, symbol, type, companyId, qty, avgCost }`; `qty` is null for a
paper that is only followed, `avgCost` (agorot) is null when it was not entered.

`POST /api/list` takes ONE operation and returns the list after it:

| Body | Effect |
| :--- | :--- |
| `{ "op": "follow", "item": {kind,id,name,…} }` | add if absent |
| `{ "op": "unfollow", "kind", "id" }` | remove (and its holding with it) |
| `{ "op": "hold", "item": {…}, "qty", "avgCost" }` | set a holding; follows if needed. Not for an index |
| `{ "op": "clearHolding", "kind", "id" }` | drop the holding, keep following |
| `{ "op": "import", "items": [...] }` | take the items this list does not have yet; change nothing that is here |

Operations, not whole lists, so the phone and the desktop can both be open and
neither overwrites what the other just did. `rev` only moves when something changed.

The list is one JSON file: `$AUTOMATE_LINUX_DIR/data/bursa/<dev|prod>/list.json`
(`BURSA_DATA_DIR` overrides the directory — the tests use that). A file that is
there but unreadable is reported, never treated as empty.

## Signing a browser in

| Route | |
| :--- | :--- |
| `GET /api/session` | `{ signedIn, configured }` |
| `POST /api/session` `{ "code": "<BURSA_API_TOKEN>" }` | sets the session cookie |
| `DELETE /api/session` | signs this browser out |
| `GET /api/session/link?exp=&sig=` | a timed link from `node scripts/make-link.mjs [minutes] [origin]` |

The cookie is an HMAC of the token (`@automatelinux/token-auth`), so the secret
never sits in a browser and rotating it signs every browser out.

## Where the data comes from

`lib/tase.ts` reads the JSON endpoints that `market.tase.co.il` itself calls. They
are undocumented; if one changes, that file is the only place to fix.
