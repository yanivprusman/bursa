# בורסה (bursa)

The Tel Aviv Stock Exchange on the phone and the desktop: the market at a glance,
any security or index one search away, a watchlist — and a **practice trading
account**: ₪100,000 of pretend money to buy and sell with, at the exchange's real
prices.

| | |
| :--- | :--- |
| Phone app | `mobile/` — Kotlin Multiplatform + Compose, Android package `com.automatelinux.bursa.dev`, launcher name **בורסה** |
| Desktop | the same server's page — `http://localhost:3169` (or `http://10.7.0.2:3169` over WireGuard) |
| Server | this directory — Next.js, dev port **3169** (`d getPort --key bursa-dev`) |
| Contract between them | [`API.md`](API.md) |

## What it does

- **שוק** — the headline indices with a month of closes each, how many of ת"א-125
  rose and fell, the day's biggest risers, fallers and most-traded, and the
  session's turnover. Every index the exchange publishes is one tap further.
- **שלי** — the practice account first: what it is worth, how far it is from the
  ₪100,000 it started with, what today did to it, the free cash. Then the holdings,
  orders waiting for the opening, the watchlist, and the latest trades.
- **Trading** — on any share, ETF or bond: **קנייה** / **מכירה**, a quantity, and
  that is all you choose. The price is the exchange's: while the paper trades, the
  order fills at once at its last price; when it does not (evenings, weekends,
  before the opening), it waits and fills at the next **opening price** — what a
  broker does with a market order. A commission of 0.1% (at least ₪5) is charged on
  every trade. There is no way to type a price or a holding in.
- **A security / an index** — price, a chart you can slide a finger along (a day
  to five years), the day's trading facts, the company, and for an index
  everything in it by weight.
- **Search** — Hebrew or English name, symbol, security number or ISIN.

## How it looks, and why

The design is **the quote board**: blue-black like a board after hours, numbers in
warm white, and exactly two colours that mean something — green for up, red for
down. Gold is the brand accent and never marks a number that moved.

- **Typeface** — IBM Plex Sans Hebrew (bundled, OFL): Hebrew, Latin and even-width
  digits from one family, so a column of prices lines up and a live price does
  not jitter.
- **Ticker strip** — the indices glide past under the search field, as on the wall
  of an exchange. It reads left to right; tapping it opens every index.
- **The lead card** — ת"א-35, large, over the line it drew today.
- **Faces** — every share, ETF and bond carries its issuer's logo (the exchange
  publishes them; the server relays them). An index, or a company with no logo,
  gets the candlestick mark.
- **Prices that tick** — when a number changes between two refreshes it flashes
  green or red for a second.
- **Icon** — the letter ב (for בורסה) in gold with two candlesticks standing in
  its opening: letter and trading chart as one shape.

## The desktop version

One screen, three panes: **your side** on the right (the practice account, holdings,
waiting orders, watchlist, latest trades), **the chosen paper** in the middle (price, a chart with a crosshair,
the day's range, trading facts, and for an index everything in it as a table),
**the market** on the left (indices, the day's mood, movers). The ticker runs
across the top; `/` jumps to search; the chosen paper is in the address bar
(`?p=s629014`), so back and forward walk through what you looked at.

It is the same list as the phone. Follow something on one and it is on the other
within half a minute.

## Where things live

**On your own server.** The watchlist and the trading account are two JSON files
on this machine (`data/bursa/<dev|prod>/list.json` and `account.json` under
automateLinux), shared by the phone and the desktop. The phone keeps a copy of the
list so it opens at once and still shows it with no connection. They are the only
private things the server holds, and they are guarded: the phone sends `BURSA_API_TOKEN` as a
bearer token (baked into the APK from the gitignored `mobile/.env`), and a browser
signs in once — paste the token, or use a timed link:

```bash
node scripts/make-link.mjs            # a 10-minute sign-in link for this machine
node scripts/make-link.mjs 60 http://10.7.0.2:3169   # an hour, for another device on the VPN
```

Market data stays open: it is what the exchange already publishes.

**The server is the only thing that talks to the exchange.** TASE has no free
public API, so `lib/tase.ts` reads the JSON endpoints its own website calls. They
are undocumented: when one changes, that file is the one place to fix, and the
phone app does not need reinstalling. It caches every response (20 s while the
market trades, 5 min once it has closed), shares identical requests in flight
and never opens more than four at once.

The phone reaches the server directly over WireGuard (`http://10.7.0.2:3169/`,
baked in from `mobile/.env`), never through nginx.

## Units — the one thing to get right

The exchange quotes every traded security in **agorot** and every index in
**points**, and this app passes them through unchanged. A holding is worth
`quantity × price / 100` shekels (`PortfolioMath.kt`, covered by tests). For a
bond the quantity is its par value in shekels.

## Working on it

```bash
d restartApp --app bursa                      # server
npm test                                      # the list store, and the desktop's formatting + portfolio maths
cd mobile && ./gradlew :shared:testDebugUnitTest   # the phone's formatting + portfolio maths
androidDeploy bursa                           # build the dev flavor and install it on the phone
```

Not covered yet: mutual funds (קרנות נאמנות that do not trade on the exchange) —
they live on a different exchange system (Maya) behind a bot check.

## Real trading — not yet

Orders go to the practice account only (`mode: "practice"` in `/api/account`). The
exchange itself takes no orders from individuals; real trades go through a broker,
and a broker with a public trading API that reaches the Tel Aviv exchange is what
would plug in behind the same buttons as a second mode — labelled, and never a
silent switch from practice.
