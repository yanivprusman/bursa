<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# bursa

Read `README.md` (what the app is, where state lives) and `API.md` (the server ↔ phone
contract, with units) before changing either side.

- **Units**: securities are in agorot, indices in points — passed through unchanged
  from the exchange. Never convert on the server; the phone does `qty × price / 100`.
- **Only `lib/tase.ts` talks to the exchange.** Its endpoints are the undocumented ones
  behind market.tase.co.il; an unknown URL gets an HTML "Request Rejected" from the WAF,
  which `tase()` turns into a `TaseError`. Period codes (`cp`) for the chart endpoint
  were measured, not documented — see `PERIOD` in `lib/market.ts`.
- **No derived prices.** An index in a list has no previous close (the exchange sends
  only the %), so `base`/`change` are null there rather than back-computed.
- **The phone app is Hebrew and always RTL** (`AppTheme`), whatever the phone's language.
  A string that is only a number goes through `Num` (forced LTR, tabular digits); a
  number inside Hebrew text is wrapped in U+2066…U+2069.
- **Never use an auto-mirrored icon for a chart** — flipped for RTL, a rising line reads
  as a falling one.
- **Holdings are user data.** Removing a held paper asks first (`ConfirmRemoveHolding`);
  installs are always `-r`, never an uninstall.
- Interactive elements carry a `testTag` (the Compose counterpart of `data-id`).

