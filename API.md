# bursa API

The server behind the phone app and the desktop page.

- **Market data** — every route below except `/api/list`, `/api/account` and
  `/api/session` — is open and read-only: it is what the exchange already publishes.
- **The owner's list** (`/api/list`) and **trading account** (`/api/account`) are
  the private things here and the only things clients write. They need
  `Authorization: Bearer <BURSA_API_TOKEN>` (the phone) or the session cookie a
  browser gets by signing in.

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
`{ kind, id, name, symbol, type, companyId }` — a paper that is followed. What is
**held** is not in the list: holdings come only from trades (`/api/account`).

`POST /api/list` takes ONE operation and returns the list after it:

| Body | Effect |
| :--- | :--- |
| `{ "op": "follow", "item": {kind,id,name,…} }` | add if absent |
| `{ "op": "unfollow", "kind", "id" }` | remove |
| `{ "op": "import", "items": [...] }` | take the items this list does not have yet; change nothing that is here. A `qty`/`avgCost` that comes along is ignored |

`hold` and `clearHolding` (typing a holding in) are gone and answer **410**.

Operations, not whole lists, so the phone and the desktop can both be open and
neither overwrites what the other just did. `rev` only moves when something changed.

## The trading account

A practice account: **pretend cash, real prices**. It starts with ₪100,000 and
trades at the exchange's own prices. A client never sends a price — only "buy 10
of 629014" — and the server reads the price from the exchange at that moment.

`GET /api/account` → the account, after filling every waiting order that may now
be filled:

| Field | |
| :--- | :--- |
| `mode` | `"practice"` (the only mode; a real broker would be another) |
| `startCash`, `cash`, `available` | agorot. `available` = `cash` less what waiting buys hold back |
| `positions[]` | `{ paper, qty, cost, avgCost }` — derived from the trades; `cost` (agorot) includes buy fees, `avgCost` is agorot per unit |
| `realized`, `fees` | agorot, over the account's life |
| `pending[]` | waiting orders, newest first |
| `orders[]` | closed orders (filled / cancelled / rejected, with `reason`), newest first, at most 100 |
| `trades[]` | `{ id, orderId, side, paper, qty, price, gross, fee, at, tradeDate, how }`, newest first. `how` = `live` (last price during the session) or `open` (opening price) |
| `unsettled[]` | waiting orders whose paper could not be priced on this read; retried on the next |
| `rules` | `{ feeRate, feeMin }` — commission 0.1%, at least ₪5 (500 agorot) |

`paper` = `{ id, name, symbol, type, companyId }`.

`POST /api/account` takes ONE operation and returns the account after it, plus `order`:

| Body | Effect |
| :--- | :--- |
| `{ "op": "order", "side": "buy"\|"sell", "id": "629014", "qty": 10 }` | a market order. `qty` is a whole number |
| `{ "op": "cancel", "orderId" }` | cancel a waiting order |
| `{ "op": "reset", "confirm": true }` | back to ₪100,000; the old account is kept as `account-until-<time>.json` |

**When an order fills** — what a broker does with a market order:

- the session is open and the paper has traded today → **now, at its last price**;
- otherwise → it **waits** and fills at the **opening price** of the first trading
  day on or after `fillFrom`. A paper that already traded today and has closed gets
  tomorrow's date — never today's opening, which is in the past.

A buy that costs more than the free cash, or a sell of more units than are held
(less those already waiting to be sold), is refused (409). A waiting buy whose
opening price turns out too expensive for the cash is **rejected**, never filled
into debt. No short selling, no indices (an index is not a security — buy an ETF).

The list and the account are JSON files in `$AUTOMATE_LINUX_DIR/data/bursa/<dev|prod>/`
(`list.json`, `account.json`; `BURSA_DATA_DIR` overrides the directory — the tests
use that). A file that is there but unreadable is reported, never treated as empty.

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
