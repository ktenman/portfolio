# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

A single owner-operator: the developer is also the sole investor whose money is tracked. There is no `User` entity and no multi-tenant model; the OAuth gate (Google/GitHub, via a separate auth service) protects one person's dataset.

Two confirmed usage situations, both first-class:

- **Phone-first daily glance.** Summary opened on mobile between other things, to see where things stand. Must survive a three-second read.
- **Deliberate desk sessions.** Desktop, sitting down on purpose: recording transactions, reviewing XIRR, digging into ETF holdings and diversification. Density and control are wanted here.

No family, shared-reader, or client audience exists. The standalone XIRR calculator at `calculator.fov.ee` is reachable by anyone, but it is **not built for anyone else** — it serves the same single owner. No surface in this product should be designed for an outside visitor, and none should carry copy that addresses one.

## Product Purpose

Hold the complete, correct picture of one real multi-broker investment portfolio, and make it answerable at two speeds. Four confirmed jobs, all of them genuine:

1. **Know where I stand** — current value, profit, XIRR, daily change.
2. **Decide what to do next** — spot concentration, rebalance, size the next buy. Diversification and ETF Breakdown are the point, not accessories.
3. **Keep an accurate record** — complete cross-broker ledger, correct cost basis, tax-ready figures.
4. **Prove the engineering** — the project is also a demonstration of its own architecture and craft.

Success is that the owner trusts the number on the screen enough to act on it without opening a broker app to check.

## Positioning

It reconciles one investor's positions across the specific Estonian/EU broker set they actually use — LHV, Swedbank, Lightyear (personal and business), Trading212, IBKR, Binance, Coinbase, and an Aviva pension — and computes XIRR over the true combined cash-flow history rather than per-account returns.

Each broker's own app sees one slice and reports a return that is only true inside that slice. Generic trackers do not carry Estonian pension and broker specifics, and do not source prices per-instrument from the provider that is correct for it (FT for stocks/ETFs, Binance for crypto, broker feeds via a Cloudflare-bypass proxy for the rest).

## Operating Context

- **Data arrives on its own.** ~15 scheduled jobs update prices and recompute analytics: FT on a market-phase adaptive cadence (60s in market hours down to 4h on weekends), Binance for crypto, Trading212/Lightyear through the Cloudflare-bypass proxy. The user reads results; they do not trigger fetches.
- **Transactions land two ways:** recorded live through the Transactions surface, and backfilled as timestamped Flyway migrations transcribed from broker statements. Both are normal.
- **Base currency is EUR.** Instruments quote in 12 currencies (USD, GBP, CHF, JPY, CAD, AUD, SEK, NOK, DKK, HKD, SGD) and are converted; a displayed figure may be a converted one.
- **Six surfaces:** Summary (`/`), Calculator, Instruments, Transactions, ETF Breakdown, Diversification.
- Deployed behind Caddy at `fov.ee`. The nav shows the running build's commit hash and date.
- Supporting services: Telegram notifications, Google Cloud Vision OCR, MinIO for holding logos, Redis caching, PostgreSQL.

## Capabilities and Constraints

**Terminology is product truth — use these words, do not paraphrase them in UI copy:** XIRR, realized profit, unrealized profit, cost basis, instrument, position, holding, _platform_ (means broker), _provider_ (means price source), daily summary.

**Enum values are product truth, generated from the backend:**

- Platform: `AVIVA, BINANCE, COINBASE, IBKR, LHV, LIGHTYEAR, LIGHTYEAR_BUSINESS, SWEDBANK, TRADING212, UNKNOWN`
- Provider: `BINANCE, FT, LIGHTYEAR, MANUAL, SYNTHETIC, TRADING212`
- Transaction type: `BUY, SELL`
- Time range (drives both the summary chart and the instruments price-change column): `1D, 2D, 3D, 1W, 1M, 3M, 6M, YTD, 1Y, 2Y, 3Y, 4Y, 5Y, MAX`

TypeScript types are generated from Kotlin DTOs into `ui/models/generated/domain-models.ts` and are never hand-edited; the domain model is owned by the backend.

**Confirmed capabilities beyond the six surfaces:**

- **XIRR windows and buy-and-hold comparison.** Per-instrument XIRR is reported across named windows and set beside a buy-and-hold annual return, so a position can be read against the alternative of having done nothing.
- **Cash tracking and platform filtering.** Cash is part of the portfolio, and platform filters plus buy-only toggles change what a figure includes. A displayed number is scoped by the filter state that produced it.
- **LLM-derived ETF intelligence.** OpenRouter classifies holdings by sector and country and resolves fund currency and TER; MinIO stores holding logos. These values are inferred by a model, not sourced from a broker.
- **Vehicle valuation, deliberately headless.** `/api/vehicle/info` performs Auto24 and Google Vision licence-plate lookups. It has no route in the SPA, it is not portfolio data, and its absence from the navigation is intentional.

**The UI framework is settled: Tailwind CSS v4.** Bootstrap and the SCSS layer are fully removed and the utility prefix has been stripped. The styling layer is three entry points: `ui/styles/theme.css` (the `@theme static` block — the single palette, plus the layer order and `@source` globs), `base.css` (element defaults and the `--transition-*` / `--z-*` scales), and `components.css`, now a barrel over nine partials in `ui/styles/components/`. New visual decisions are declared as tokens in `theme.css` and consumed as `var(--color-*)` or a generated utility; a second palette layer is a fork, not a shortcut.

**Every UI change is gated by a pixel harness.** Playwright captures each route at three viewports (390×844, 768×1024, 1440×900) at `maxDiffPixels: 0`, with baselines in `docs/superpowers/baseline/`, and every capture must stub its data routes so a shot never reads the live database. Any visual change is therefore a baseline change: work is not finished until its baselines are re-recorded deliberately, and an unexplained diff is a regression, not noise.

Architecture is fixed: Vue 3 SPA over a Spring Boot REST API. No server-rendered pages.

## Evidence on Hand

- `screenshots/app.png`, `screenshots/app-v2024.png` — real screenshots of the running app, used in the README.
- `docs/architecture/*.puml` — seven C4 and sequence diagrams, generated to SVG by `scripts/generate-diagrams.sh`.
- `docs/aviva/` — real pension statements, already reconciled against recorded transactions.
- 200+ Flyway migrations carrying the owner's actual broker transaction history. The app runs on real money, not fixtures.

**Absences future work must not fabricate:** there is no logo or wordmark (the favicon is Vite's default), no voice or brand guide, no testimonials, no customers, no pricing, no marketing copy. The product name in use is simply "Portfolio".

## Product Principles

1. **The numbers are the product.** Correctness and legibility of a figure outrank any expressive treatment of it. Never let a visual choice change what a number appears to say.
2. **Two speeds, one system.** Every surface must survive a three-second phone glance _and_ a long desk session — the same screen doing both, not two divergent designs.
3. **Cross-broker truth is the differentiator.** Anything that collapses the portfolio back into per-broker silos removes the reason this exists.
4. **Real data or an honest empty state.** The app runs on an actual portfolio; placeholder numbers and demo figures are never acceptable.
5. **The engineering is meant to be visible.** The build hash in the nav, the diagrams, the test coverage — visible craft is part of what the project is for.

## Accessibility & Inclusion

**WCAG 2.2 AA is the committed bar.** Contrast, visible focus, keyboard operability, and target size are held to AA regardless of the app having exactly one known user — the commitment follows from the product's own principle that the engineering is meant to be visible, not from a documented user need.

What the code already does is the floor to preserve, not the ceiling: `:focus-visible` outlines rather than suppressed rings, 44px touch targets below 768px, and ARIA attributes or semantic roles across roughly 15 component files. `prefers-reduced-motion` is honoured everywhere except the price flash and spinners, which are deliberately exempt because they carry information rather than decoration.
