# בורסה (bursa)

The Tel Aviv Stock Exchange on the phone: the market at a glance, any security or
index one search away, a watchlist, and what your own holdings are worth today.

| | |
| :--- | :--- |
| Phone app | `mobile/` — Kotlin Multiplatform + Compose, Android package `com.automatelinux.bursa.dev`, launcher name **בורסה** |
| Server | this directory — Next.js, dev port **3169** (`d getPort --key bursa-dev`) |
| Contract between them | [`API.md`](API.md) |

## What it does

- **שוק** — the headline indices with a month of closes each, how many of ת"א-125
  rose and fell, the day's biggest risers, fallers and most-traded, and the
  session's turnover. Every index the exchange publishes is one tap further.
- **שלי** — what you follow and what you hold. With holdings, the first thing on
  screen is what the portfolio is worth and what today did to it, in shekels.
- **A security / an index** — price, a chart you can slide a finger along (a day
  to five years), the day's trading facts, the company, and for an index
  everything in it by weight.
- **Search** — Hebrew or English name, symbol, security number or ISIN.

## Where things live

**The server holds nothing personal.** The watchlist and the holdings are stored
on the phone (`Portfolio.kt`, app-private storage, included in Android's backup).
The server only relays what the exchange already publishes, which is why it has
no accounts and no auth.

**The server is the only thing that talks to the exchange.** TASE has no free
public API, so `lib/tase.ts` reads the JSON endpoints its own website calls. They
are undocumented: when one changes, that file is the one place to fix, and the
phone app does not need reinstalling. It caches every response (20 s while the
market trades, 5 min once it has closed), shares identical requests in flight
and never opens more than four at once.

The phone reaches the server directly over WireGuard (`http://10.7.0.2:3169/`,
baked in from the gitignored `mobile/.env`), never through nginx.

## Units — the one thing to get right

The exchange quotes every traded security in **agorot** and every index in
**points**, and this app passes them through unchanged. A holding is worth
`quantity × price / 100` shekels (`PortfolioMath.kt`, covered by tests). For a
bond the quantity is its par value in shekels.

## Working on it

```bash
d restartApp --app bursa                      # server
cd mobile && ./gradlew :shared:testDebugUnitTest   # formatting + portfolio maths
androidDeploy bursa                           # build the dev flavor and install it on the phone
```

Not covered yet: mutual funds (קרנות נאמנות that do not trade on the exchange) —
they live on a different exchange system (Maya) behind a bot check.
