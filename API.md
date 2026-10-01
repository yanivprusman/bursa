# bursa API

Read-only market data for the phone app. Every route is a `GET` that returns JSON;
a failure is `{ "error": "<Hebrew message>" }` with a 4xx/5xx status.

Nothing personal lives here — the watchlist and the holdings stay on the phone —
so there is no auth: the server holds only what the exchange already publishes.

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
| `/api/market` | `{ open, tradeDate, tradeTime, indices[], movers{gainers,losers,active}, breadth, turnovers[] }` — the headline indices carry `spark`, a month of closes |
| `/api/indices` | `{ indices[] }` — every index, with `category` |
| `/api/index/<id>` | one index plus `components[]` (sorted by weight) |
| `/api/security/<id>` | one security: quote, day range, yields, bond terms, `about` |
| `/api/quotes?ids=s629014,i142` | `{ quotes[], missing[] }` — `s` = security, `i` = index, up to 80 |
| `/api/chart?kind=security\|index&id=<id>&range=<r>` | `{ range, base, points[{t,v}] }`; `r` ∈ `1d 1w 1m 3m 6m 1y 3y 5y`, at most 240 points |
| `/api/search?q=<text>` | `{ hits[] }` — Hebrew or English name, symbol, ISIN or number |

An index in a list (`/api/market`, `/api/indices`, `/api/quotes`) has `base` and
`change` = `null`: the exchange's list carries only the percentage, and a previous
close derived from a rounded percentage would be wrong in the second decimal.
`/api/index/<id>` has the real one.

## Where the data comes from

`lib/tase.ts` reads the JSON endpoints that `market.tase.co.il` itself calls. They
are undocumented; if one changes, that file is the only place to fix.
