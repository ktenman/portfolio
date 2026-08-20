# Visual baseline index

Pre-migration reference screenshots for the Bootstrap → Tailwind migration
(`docs/superpowers/specs/2026-08-07-bootstrap-to-tailwind-design.md`, Phase 0).

Produced by `npm run visual:update`, verified by `npm run visual`. The harness is
`playwright.config.ts` plus `ui/tests/visual/{routes,states}.spec.ts`; shared helpers live in
`ui/tests/visual/settle.ts` and the stubs in `ui/tests/visual/etf-fixture.ts` (`/etf-breakdown`),
`ui/tests/visual/instruments-fixture.ts` (`/instruments`), `ui/tests/visual/summary-fixture.ts`
(`/`), `ui/tests/visual/transactions-fixture.ts` (`/transactions`),
`ui/tests/visual/diversification-fixture.ts` (`/diversification`),
`ui/tests/visual/calculator-fixture.ts` (`/calculator`), `ui/tests/visual/windows-fixture.ts` (the
two XIRR dialogs) and `ui/tests/visual/build-info-fixture.ts` (the navbar version string, installed
on every capture).

Every capture is fixture-backed and **nothing is masked**.

Viewports: **mobile** 390×844, **tablet** 768×1024, **desktop** 1440×900. All at `scale: 'css'`,
`animations: 'disabled'`, `caret: 'hide'`, `maxDiffPixels: 0`.

## Readiness protocol

`networkidle` is not a readiness condition in this app — `/instruments` polls every ~2.1 s forever
and a late poll re-flows table column widths. Every capture therefore runs:

1. `page.goto(route)` then `waitForLoadState('networkidle')`
2. `waitForPageToSettle` — page signature identical across 4 samples 400 ms apart
3. the interaction that opens the state, if any
4. `waitForPageToSettle` again
5. `page.route('**/api/**', r => r.abort())` — no further data can land
6. screenshot

The signature is `document.documentElement.scrollHeight` joined with a hash of every `<canvas>`'s
`toDataURL()`, so layout and painted chart pixels settle on the same four samples at no extra polling
cost.

Step 2 is what makes the framing deterministic. `page.goto` resolves while the page is still growing
(`/instruments` desktop: 2293 → 2337 → 2401 px over ~165 ms after `networkidle`), and a Playwright
click scrolls its target into view clamped against `scrollHeight - innerHeight` **at that instant**.
Interacting before the height settles therefore leaves the page behind an overlay parked at a
scroll offset below the settled maximum, which no later gate corrects — the flake was a background
scroll race, not late-landing data.

Modal captures insert two extra gates between 3 and 4: `waitForBoxHeightToSettle` on
`.modal.show .modal-content`, and `waitForBackdropToSettle`, which requires `.modal-backdrop.show`
to be present with a computed opacity identical across 4 samples. The backdrop opacity is captured
as the application renders it (currently `0.5`); nothing is injected to force it.

## Why baselines go red when `theme.css` is "fixed"

`ui/styles/theme.css:4` imports `tailwindcss/utilities.css` with `prefix(tw)` but deliberately
**without** `layer(utilities)`, so the `utilities` layer declared on line 1 stays empty and every
`tw:` utility is unlayered. That is load-bearing. Bootstrap's SCSS is also unlayered and is imported
first (`ui/main.ts`), so utilities win on ordinary specificity plus source order. Adding the
canonical `layer(utilities)` puts every utility below all of Bootstrap regardless of specificity and
silently reverts every migrated component — verified by mutation, which turns the three
`/instruments` baselines red with `h2` margin-bottom reverting 0px → 8px. If these captures fail
after an apparently unrelated stylesheet cleanup, check that import first.

The same cascade explains the other trap: an unprefixed utility does not resolve to nothing here, it
inherits **Bootstrap's** same-named utility at a different value. Dropping `tw:` from `tw:px-3`
yields Bootstrap's `.px-3` at 16px, not 0.

## Volatility handling

Every endpoint that reaches a capture is stubbed, so no capture is masked. The two mask lists
(`VOLATILE_SELECTORS` and `MODAL_VOLATILE_SELECTORS`, formerly `ui/tests/visual/volatile.ts`) and
the `visibility: hidden` pass that neutralised the page behind an overlay were deleted once the last
live route was stubbed: with the data fixed, every selector on those lists pointed at deterministic
content, and masking it hid exactly what the migration changes — gain/loss colour, numeric-cell
typography, and the calculator's Year-by-Year table. Masking was never a complete answer anyway.
Playwright paints masks at `z-index: 2147483646`, so masking the page behind a dialog buries the
dialog; and a mask hides pixels but not the box, so content that re-renders at a different width
still moves every baseline at once — measured, when `/api/build-info` was stubbed: a one-pixel shift
in the mask's left edge turned 14 captures red at 26 pixels each.

- **`/etf-breakdown` is served a fixture, not the database.** It could not have been masked into
  stability: the page shows 200 of 3300+ holdings sorted by value, background jobs enrich those
  holdings while the suite runs, and the row order — therefore the page height — moves with them.
  `stubEtfBreakdown`
  (`ui/tests/visual/etf-fixture.ts`) fulfils five endpoints:

  | Pattern                                      | Response                          |
  | -------------------------------------------- | --------------------------------- |
  | `**/api/etf-breakdown**`                     | ten fixed holdings                |
  | `**/api/instruments**`                       | the fifteen fixture ETF positions |
  | `/\/api\/logos\/<uuid>(\?\|$)/` (a `RegExp`) | a fixed 32×32 SVG                 |
  | `**/api/logos/*/candidates`                  | `[]`                              |
  | `**/api/logos/prefetch`                      | `204`                             |

  The logo pattern is a UUID-shaped `RegExp`, not `**/api/logos/*`, because that glob also swallows
  `/api/logos/search` and `/api/logos/prefetch` — correctness would then depend on Playwright
  matching the most recently registered handler first. `/api/logos/search` is deliberately _not_
  stubbed; nothing currently reaches it, and the capture that would (clicking the null-uuid row's
  placeholder) does not exist. Add a stub before writing one.

  The instruments stub is what makes the ETF filter buttons carry currency flags and the header
  render its "Fund Currency" card: both read `fundCurrency` off `/api/instruments`, keyed by the
  full ETF symbol. Fifteen positions across EUR/GBP/USD keep that button group wrapping onto more
  than one row at every viewport.

  The stub is installed by the two captures that render that route — `route etf-breakdown` and
  `modal logo-replacement` — and by nothing else; `state-spinner` still holds the real endpoint
  open. Ten holdings keep `hasMore` false, so `.load-more-row` never renders. Changing the fixture
  re-records five baselines.

  The mobile capture of this route is **510px wide at a 390px viewport**, because the page overflows
  horizontally: the widest in-flow elements are the Fund Currency card's value spans, which carry
  `.currency-value` and `tw:min-w-16`. The live page overflows the same way (`scrollWidth: 530`), so
  the wider canvas is faithful rather than a fixture artifact — the earlier 390px baseline hid it only by
  omitting the card. A later task that fixes the overflow turns this baseline red on purpose and
  must re-record it deliberately.

- **`/instruments` is served a fixture, not the database.** It could not have been masked into
  stability either. The table sorts by `currentValue`, so a price tick reorders rows, and the
  rendered row _set_ moves too because `showActiveOnly` (default on) keeps only `currentValue > 0`.
  Masking the value cells left the ordering unmasked, and re-recording buys days rather than a gate —
  the captures agree to the pixel only while markets are closed. `stubInstruments`
  (`ui/tests/visual/instruments-fixture.ts`) fulfils one endpoint:

  | Pattern                                    | Response                                           |
  | ------------------------------------------ | -------------------------------------------------- |
  | `/\/api\/instruments(\?\|$)/` (a `RegExp`) | fourteen fixed instruments, `portfolioXirr` 0.1842 |

  The pattern is anchored with `(\?|$)` rather than written as `**/api/instruments**` so it cannot
  swallow `/api/instruments/refresh-prices` or `/api/instruments/{id}`. It answers both of
  `instruments-view.vue`'s queries — the unfiltered `instruments-all` that builds the platform
  filter buttons, and the period-keyed `instruments` that fills the table — because both request
  the same path.

  The stub is installed **per capture**, on the four tests that render `/instruments` with data
  (`route instruments`, `modal instrument`, `modal xirr-windows`, `modal annual-windows`) and on
  nothing else. That scoping is load-bearing in both directions: `stubEtfBreakdown` answers
  `**/api/instruments**` with its own fifteen ETF positions, so a file-scoped registration would
  hijack `/etf-breakdown`'s currency card; and `state-error` stubs the same path with a 500, so a
  file-scoped registration would erase the error state. Changing the fixture re-records nine
  baselines.

  `portfolioXirr` and at least one row's `xirrAnnualReturn` must stay non-null. The totals row
  disables both `.xirr-trigger` buttons when they are null, and the two windows captures click
  them. `currentValue` must stay non-null on every row for the same reason `showActiveOnly` exists.

  `/api/enums` is deliberately _not_ stubbed: the platform badge and filter-button labels
  ("Lightyear Business", "Trading 212") come from it, and enum values change only on deploy.

- **`/` is served a fixture, not the database.** Masking could not have stabilised it: a new daily
  summary row appears every midnight, the table grows by one row, and everything below it shifts. Row
  _count_ was inside no mask, so the whole full-page capture moved. `stubPortfolioSummary`
  (`ui/tests/visual/summary-fixture.ts`) fulfils three endpoints:

  | Pattern                                         | Response                                              |
  | ----------------------------------------------- | ----------------------------------------------------- |
  | `/\/api\/portfolio-summary\/historical(\?\|$)/` | 186 fixed rows, `totalPages` 1                        |
  | `/\/api\/portfolio-summary\/current(\?\|$)/`    | one fixed row dated `2025-12-31`                      |
  | `/\/api\/transactions\/platforms(\?\|$)/`       | the seven platform names the enum list already labels |

  All three are anchored `RegExp`s rather than `**/api/portfolio-summary/**` so that none of them can
  swallow `/api/portfolio-summary/recalculate`, whose abort guard is destructive-action protection,
  or `/api/portfolio-summary/{xirr,annual}-windows`. 186 rows is the page size
  `use-portfolio-summary-query.ts` requests; the fixture's current row carries a date absent from
  the historical page, so `mergeHistoricalWithCurrent` appends rather than replaces and the table
  renders 187 rows — the same count, and on desktop the same 11156 px page height, as the live
  database produced when the fixture was written. `totalPages: 1` keeps `useInfiniteScroll` from ever
  requesting a second page.

  The stub is installed **per capture**, on `route summary`, `modal confirm` and the four `toast`
  captures. `state loading skeleton` deliberately does **not** get it: that test holds
  `**/api/portfolio-summary/**` open for 20 s to render the skeleton, and a stub would fulfil the
  request and delete the state. Registration order in `modal confirm` puts the stub first and the
  `recalculate` abort second, so the abort is the most recently registered handler for its URL.

  Every fixture date is a fixed literal in the past, so no row is ever "today". Changing the fixture
  re-records eight baselines.

- **`/transactions` is served a fixture, not the database.** Masking could not have stabilised it
  either, and the mechanism is not the one the row-count argument predicts. Measured on the live
  database while the masks still existed: six of 673 rows changed `unrealizedProfit` inside four
  minutes, yet re-rendering that drift moved **0 pixels** on mobile and 0 on desktop — the profit
  masks absorbed it, which is another way of saying they were absorbing the gain/loss rendering the
  migration changes. Forcing every row's `unrealizedProfit` up 13% moved 14 mobile pixels. What moved
  the capture even through the masks was **structure**: appending one transaction moved 13,761,300
  mobile pixels and grew the page 148040 → 148283, and re-deriving FIFO state changes the
  `remainingQuantity` text — perturbing the 193 rows that render a "Remaining" line by 3% moved
  23,311 pixels. So the exposure is a new transaction or a new sell, not the tick-by-tick quote drift
  `/` had. `stubTransactions`
  (`ui/tests/visual/transactions-fixture.ts`) fulfils one endpoint:

  | Pattern                        | Response                                          |
  | ------------------------------ | ------------------------------------------------- |
  | `/\/api\/transactions(\?\|$)/` | 673 fixed rows (456 BUY, 217 SELL) plus a summary |

  One anchored `RegExp` covers both requests the route makes — the bare `/api/transactions` that
  fills the platform filter and the `?platforms=…` one the table renders — while `(\?|$)` makes it
  structurally unable to swallow `/api/transactions/platforms` or `/api/transactions/{id}`. The
  platform filter is derived from the **unfiltered** payload, not from `/api/transactions/platforms`,
  which this route never calls; the fixture's seven platforms reproduce the live query string
  exactly. The fixture data is invented (`TST*` symbols, `Ørsted`, `Société Générale`), sized to the
  live shape it replaced: 673 rows across 33 instruments, the same seven platform counts, 193 rows
  with a partial `remainingQuantity`, 29 with a commission, 102 distinct dates from 2020-01-02 to
  2026-08-06, and a rendered-amount width distribution matched bucket for bucket. Desktop lands on
  the same 56855 px page height as the live data; mobile is 148994 (was 148040) and tablet 74824
  (was 73570).

  Deliberate irregularities the live payload never produced: one `id: null`, three `realizedProfit:
null` and three `averageCost: null` rows (all render `0.00`), three quantities below 0.0001 that
  take the `formatScientific` path, and 30 non-EUR rows — a GBP pension fund and a USD share — that
  are the only coverage of the `item.currency !== 'EUR'` branch in `transaction-table.vue`. The
  backend converts everything to EUR, so that branch was previously dead in every baseline.

  The stub is installed **per capture**, on `route transactions` and `dropdown quick dates`.
  `state empty table` deliberately does **not** get it: that test registers its own
  `**/api/transactions**` handler returning an empty list to render `.alert-info`, and because
  Playwright matches most-recently-registered first, hoisting the stub into a `beforeEach` would
  win over it and delete the state. Changing the fixture re-records four baselines.

- **`/diversification` is served a fixture, not the database.** It is the one fixture that does
  **not** reproduce today's live page, deliberately. The dev database holds no persisted
  `DiversificationConfig` row, so `/api/diversification/config` answers `204`, the live page is a
  single blank "Select ETF" row, and the pre-fixture baseline gated almost nothing — no allocation
  table, no stats cards, no breakdown tables, which is most of what Phase 3 migrates.
  `stubDiversification` (`ui/tests/visual/diversification-fixture.ts`) fulfils three endpoints and
  delegates to `stubInstruments`:

  | Pattern                                           | Response                                 |
  | ------------------------------------------------- | ---------------------------------------- |
  | `/\/api\/diversification\/available-etfs(\?\|$)/` | twelve `EtfDetailDto` entries            |
  | `/\/api\/diversification\/config(\?\|$)/`         | a `CachedState` with five allocations    |
  | `/\/api\/diversification\/calculate(\?\|$)/`      | ten holdings, ten sectors, ten countries |

  `diversification-calculator.vue:204` also calls `instrumentsService.getAll()` on mount, so the
  stub layers `stubInstruments` underneath; without it the platform filter buttons and the Current
  column are database-backed. The twelve available ETFs cover the branches the components have — a
  `null` `ter`, a `null` `currentPrice`, EUR/USD/GBP `fundCurrency` so the Fund Currency card
  renders three rows, and a non-ASCII name. The live endpoint returns thirty, but only allocated
  rows render and every capture leaves the `<select>` closed. The `/config` stub also intercepts the
  `PUT` that `use-diversification-config.ts:16` fires, which is why the capture can no longer write
  to the developer's database. Changing the fixture re-records seven baselines.

- **`/calculator` is served a fixture, not the database.** The route looked inert because
  `calculator.vue` imports no service; the fetch sits one level down, where `use-calculator.ts:44`
  sets `queryFn: utilityService.getCalculationResult` and `utility-service.ts:11` resolves that to
  `API_ENDPOINTS.CALCULATOR`. `stubCalculator` (`ui/tests/visual/calculator-fixture.ts`) fulfils it:

  | Pattern                      | Response                                            |
  | ---------------------------- | --------------------------------------------------- |
  | `/\/api\/calculator(\?\|$)/` | 139 cash flows plus `median`, `average` and `total` |

  The response drives three things the baseline renders: `#annualReturnRate`, which the composable
  overwrites with `calculationResult.median` on a fresh load; `#initialWorth`, set from `total`; and
  the rolling-XIRR bar chart, which plots `cashFlows` directly and gains a bar per new flow. The
  fixture mirrors the live payload's shape — 139 flows on a 28-day cadence from `2016-01-08`, an
  exponential ramp from about −74 to +24 — and carries **full-precision doubles**: `median` lands in
  an `<input>` verbatim, so a round number would not render the digit string the app shows. Changing
  the fixture re-records three baselines.

- **The two XIRR window dialogs are served a fixture, not the database.**
  `xirr-windows-modal.vue:83` and `annual-windows-modal.vue:83` call
  `portfolioSummaryService.getXirrWindows(...)` / `getAnnualWindows(...)`, and `stubInstruments` —
  anchored to `/\/api\/instruments(\?|$)/` — structurally cannot match those paths, so both dialogs
  were live-data-backed even though the page behind them was not. Worse, each row's `fromDate` is
  computed relative to today, so the Since column changed daily. `stubWindows`
  (`ui/tests/visual/windows-fixture.ts`) fulfils both:

  | Pattern                                             | Response                         |
  | --------------------------------------------------- | -------------------------------- |
  | `/\/api\/portfolio-summary\/xirr-windows(\?\|$)/`   | six periods with `xirr`          |
  | `/\/api\/portfolio-summary\/annual-windows(\?\|$)/` | the same six with `annualReturn` |

  One negative value per dialog is deliberate: it is the only coverage of `returnClass`'s loss
  branch, which sat under a mask until the masks came off. The `3Y` row carries a `null` `fromDate`
  to cover the `—` fallback the dialog's own footnote describes. Changing the fixture re-records
  four baselines.

- **`/api/build-info` is served a fixture on every capture**, through a `test.beforeEach` rather than
  a per-capture stub — file-scoped in `routes.spec.ts`, and in `states.spec.ts` one hook per
  `describe`, declared immediately after that block's skip hook. That hoisting is safe here precisely
  where it is not safe for the route fixtures: no capture registers a competing handler for that
  path, so there is no most-recently-registered-wins interaction to get wrong. It cannot be hoisted
  any further in `states.spec.ts`, though, and the reason is easy to miss: Playwright resolves
  fixtures per hook, so a hook that asks for `page` ahead of the skip hook builds a browser context
  for all 25 skipped tests. Measured on a 12-skip probe, the `page` fixture is built 0 times in the
  current order and 12 times reversed. Merging the two hooks into one has the same cost as hoisting,
  for the same reason. `nav-bar.vue:17` renders
  `buildInfo.hash.substring(0, 7)` and `formatDate(buildInfo.time)`, and the live endpoint answers
  `{"hash":"unknown","time":"<now>"}` — the timestamp is the moment of the request. Changing the
  fixture re-records every baseline that shows the navbar.

- **The portfolio summary's "today" row is never rendered as today.** `portfolio-summary.vue` marks
  that row `.font-weight-bold` but decides "today" with `new Date().toISOString()`, which is UTC.
  Every fixture date is a fixed literal in the past, so the class never applies and the bold-today
  rendering is covered by no baseline.

## Routes — `ui/tests/visual/routes.spec.ts`

Full-page (`fullPage: true`), no interaction. Every route carries a stub, and `stubBuildInfo` runs
for all six through a file-scoped `beforeEach`. Two bounds narrow what is actually compared:

- **The capture is clipped to the first `MAX_CAPTURE_HEIGHT` (12000) pixels**, at
  `document.documentElement.scrollWidth` rather than the viewport width — the viewport width would
  crop `route-diversification-tablet` from its real 785 px back to 768 and hide the overflow that
  produced it. Chromium paints `fullPage` unreliably past roughly 16000 px, and `/transactions` runs
  to 56854 px on desktop and 147160 on mobile: the un-clipped desktop capture returned a 38.7 M-pixel
  diff on CI while passing locally. Everything below 12000 px on those pages is uncovered — on
  `/transactions` that is 79 % of the fixture on desktop and 92 % on mobile, and it costs the pixel
  coverage of the `formatScientific` quantities, the `realizedProfit: null` row and the
  `averageCost: null` row, all of which sort below the cut. Only `id: null` stays inside every clip.
- **Every `<canvas>` is masked**, so no chart pixel is compared on any route. See the canvas note
  under Known limits.

| File                                | Route              | Viewport |
| ----------------------------------- | ------------------ | -------- |
| `route-summary-mobile.png`          | `/`                | mobile   |
| `route-summary-tablet.png`          | `/`                | tablet   |
| `route-summary-desktop.png`         | `/`                | desktop  |
| `route-transactions-mobile.png`     | `/transactions`    | mobile   |
| `route-transactions-tablet.png`     | `/transactions`    | tablet   |
| `route-transactions-desktop.png`    | `/transactions`    | desktop  |
| `route-instruments-mobile.png`      | `/instruments`     | mobile   |
| `route-instruments-tablet.png`      | `/instruments`     | tablet   |
| `route-instruments-desktop.png`     | `/instruments`     | desktop  |
| `route-etf-breakdown-mobile.png`    | `/etf-breakdown`   | mobile   |
| `route-etf-breakdown-tablet.png`    | `/etf-breakdown`   | tablet   |
| `route-etf-breakdown-desktop.png`   | `/etf-breakdown`   | desktop  |
| `route-diversification-mobile.png`  | `/diversification` | mobile   |
| `route-diversification-tablet.png`  | `/diversification` | tablet   |
| `route-diversification-desktop.png` | `/diversification` | desktop  |
| `route-calculator-mobile.png`       | `/calculator`      | mobile   |
| `route-calculator-tablet.png`       | `/calculator`      | tablet   |
| `route-calculator-desktop.png`      | `/calculator`      | desktop  |

## Modals — `ui/tests/visual/states.spec.ts`

Viewport screenshots (`fullPage` omitted, so the frame is the viewport) containing the dialog, the
backdrop, and the page behind it. Tablet is skipped: overlays render identically to desktop at
768 px. Each capture asserts the open dialog's `.modal-title` before shooting, so a positional
trigger cannot silently produce the wrong baseline.

| File                                 | Route              | Viewport | Interaction → asserted title                                                                                 |
| ------------------------------------ | ------------------ | -------- | ------------------------------------------------------------------------------------------------------------ |
| `modal-instrument-mobile.png`        | `/instruments`     | mobile   | inject + click a hidden `[data-bs-toggle="modal"][data-bs-target="#instrumentModal"]` → "Add New Instrument" |
| `modal-instrument-desktop.png`       | `/instruments`     | desktop  | as above                                                                                                     |
| `modal-xirr-windows-mobile.png`      | `/instruments`     | mobile   | `.xirr-trigger, .xirr-trigger-mobile` visible-filtered `.nth(0)` → "Annualized return over time"             |
| `modal-xirr-windows-desktop.png`     | `/instruments`     | desktop  | as above                                                                                                     |
| `modal-annual-windows-mobile.png`    | `/instruments`     | mobile   | same locator `.nth(1)` → "Buy-and-hold annualized return"                                                    |
| `modal-annual-windows-desktop.png`   | `/instruments`     | desktop  | as above                                                                                                     |
| `modal-logo-replacement-mobile.png`  | `/etf-breakdown`   | mobile   | first visible `.company-logo.clickable` → `/^Replace Logo: /`                                                |
| `modal-logo-replacement-desktop.png` | `/etf-breakdown`   | desktop  | as above                                                                                                     |
| `modal-config-export-mobile.png`     | `/diversification` | mobile   | `button[aria-label="Export"]` → "Export Configuration"                                                       |
| `modal-config-export-desktop.png`    | `/diversification` | desktop  | as above                                                                                                     |
| `modal-config-import-mobile.png`     | `/diversification` | mobile   | `button[aria-label="Import"]` → "Import Configuration"                                                       |
| `modal-config-import-desktop.png`    | `/diversification` | desktop  | as above                                                                                                     |
| `modal-confirm-mobile.png`           | `/`                | mobile   | `button:has-text("Recalculate Data")` → "Recalculate Portfolio Data"                                         |
| `modal-confirm-desktop.png`          | `/`                | desktop  | as above                                                                                                     |

**The confirm dialog is destructive.** Its confirm button deletes all portfolio summary data and
recalculates from scratch. The capture dismisses it via `[data-testid="confirmDialogCancelButton"]`
(Escape is a no-op — `confirm-dialog.vue` builds the modal with `keyboard: false`) and installs
`page.route('**/api/portfolio-summary/recalculate**', r => r.abort())` before navigating — after
`stubPortfolioSummary`, so the abort is matched first. Never click confirm; keep the route abort.

`#instrumentModal` has no user-reachable trigger on `/instruments` — `instruments-view.vue` sets
`:show-add-button="false"`. The injected trigger reproduces `openAddModal()` exactly: Bootstrap's
data-api calls `Modal.getOrCreateInstance`, reusing the instance `useBootstrapModal` created on
mount, and `selectedItem` is already `null` on a fresh load.

## Dropdown, toasts and states — `ui/tests/visual/states.spec.ts`

Desktop only.

| File                               | State                              | Interaction                                                                                                   |
| ---------------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `dropdown-quick-dates-desktop.png` | `/transactions` "Quick Dates" open | click `[data-bs-toggle="dropdown"]`, await `.dropdown-menu.show`; viewport shot over the fixture-backed table |
| `toast-success-desktop.png`        | `/` + success toast                | `import('/composables/use-toast.ts')` then `useToast().success('Baseline success message')`                   |
| `toast-error-desktop.png`          | `/` + error toast                  | same, `.error(...)`                                                                                           |
| `toast-info-desktop.png`           | `/` + info toast                   | same, `.info(...)`                                                                                            |
| `toast-warning-desktop.png`        | `/` + warning toast                | same, `.warning(...)`                                                                                         |
| `state-loading-desktop.png`        | `/` loading skeleton               | `**/api/portfolio-summary/**` held 20 s, then assert `.skeleton`; no summary fixture, deliberately            |
| `state-spinner-desktop.png`        | `/etf-breakdown` loading spinner   | `**/api/etf-breakdown**` held 20 s, then assert the first `.loading-spinner`                                  |
| `state-empty-desktop.png`          | `/transactions` empty              | `**/api/transactions**` stubbed with an empty transaction list, then assert `.alert-info`                     |
| `state-error-desktop.png`          | `/instruments` error               | `**/api/instruments**` stubbed 500; **element-scoped to `.alert-danger`**                                     |

Toasts are fired _after_ the freeze, because they auto-hide in 4000–7500 ms while the settle gate
needs ≥1.6 s. `/transactions` cannot produce an error state at all (`transactions-view.vue` never
destructures `isError`), which is why the error capture moved to `/instruments`.

## Known limits

- **`state-error` is a crop, not a page.** `/instruments` derives its vue-query `queryKey` from
  fetched data and retries, so under a stubbed 500 the page oscillates between skeleton and alert.
  The element scope lets Playwright poll for the settled alert. The error _page layout_ is
  unbaselined.
- **`state-loading` is timing-coupled.** The 20 s route hold must outlast the ≥1.6 s settle plus the
  capture. If it ever doesn't, `--update-snapshots` records a half-transitioned page without
  failing, because the `.skeleton` assertion has already passed.
- **Every pixel is now gated, which cuts both ways.** With the masks gone, a capture that fails on a
  value-only delta with no layout shift is a real regression — a fixture-backed number rendered
  differently — not a missing selector. There is no longer a way to absorb a diff without either
  changing the fixture or re-recording, and both are visible in review.
- **`state-error` never reaches the real `/api/instruments` either.** It installs its own 500 on that
  path, so it is neither fixture-driven nor live — it is independent of `stubInstruments` in both
  directions and will not notice a fixture change.
- **`modal-logo-replacement` no longer varies in height.** The stub returns an empty candidate list,
  so the body is always the single "No logo candidates found" line, and `.first()` always picks the
  fixture's first holding. Both the title — which carries the holding name — and the body are now
  gated; removing the empty-candidates stub would put provider imagery in frame and turn both
  captures red.
- **Scroll offset follows the fixture** for the XIRR/annual captures: clicking a totals-row trigger
  scrolls it into view, so the page behind is framed by the row count. That count is now fixed at
  fourteen, but adding or removing a fixture row moves the framing and re-records both captures.
  Both triggers share one totals `<tr>`, so a settled page yields the _same_ offset for both — the
  clamp is `scrollHeight - innerHeight`, currently 1501 px on desktop. `modal-xirr-windows-desktop`
  and `modal-annual-windows-desktop` predated step 2 and were recorded mid-growth at 1480 and 1435;
  they were re-recorded at the settled 1501 and now agree with each other.
- **The Chart.js canvases are masked in `routes.spec.ts` and unmasked everywhere else.** Two
  separate defences were needed. First, they flake mid-animation: a freshly recorded
  `route-calculator-desktop` failed on immediate re-run at 1858 pixels, bounded to
  `791x513+541+512`, with the "XIRR Rolling Result (ASAP)" canvas caught part-drawn.
  `animations: 'disabled'` does not cover that, because it disables CSS animations and Chart.js
  draws on `requestAnimationFrame`; the settle signature folds in a hash of each canvas's
  `toDataURL()`, so a still-animating chart keeps resetting the sample count. That fix holds for the
  viewport captures in `states.spec.ts`, which stay unmasked. Second, and only on the route
  captures, a settled canvas is still not reproducible — and the cause is product code, not the
  harness. `bar-chart.vue` derives its point count from `ctx.canvas.width` at config-build time
  (`Math.max(Math.floor(ctx.canvas.width / 15), 26)`), while `useChartLifecycle` builds the config
  twice, once from `onMounted` and again from `watch(data)`. The two builds can disagree on how many
  points `applyASAP` emits, which moves every bar edge and the `x` tick modulo together. Ten
  consecutive container runs of `/calculator` at tablet held the canvas bitmap at an exact 472×236
  and still produced two distinct renders, 2 runs in 10; on CI that surfaced as 9376 pixels over
  `467x190+277+519` on `route-calculator-tablet`, which is what made the job red.
  `mask: [page.locator('canvas')]` paints those regions out, so the alarm is silenced rather than
  the race fixed. Note the cost: `states.spec.ts` never visits `/calculator`, so the line and bar
  charts now have no pixel coverage anywhere. Chart configuration is covered instead by
  `ui/composables/use-chart-lifecycle.test.ts` and `ui/components/charts/portfolio-chart.test.ts`.
- **`modal-confirm-desktop` is not gated on `/`'s data.** At 1440×900 the chart fills the
  viewport below the platform filter, so no table row is visible behind the backdrop and the capture
  was byte-identical before and after `/` became fixture-driven. Only the mobile confirm capture,
  whose smaller chart leaves cards visible, moved. Do not assume a `/`-based viewport capture is
  covered by the fixture — check whether the table is actually in frame.
- **`/api/enums` is still live on every route**, `/` included. It supplies the platform badge and
  filter-button labels ("Lightyear Business", "Trading 212"), which change only on deploy. That is
  the last unstubbed endpoint any capture reaches, and it has been accepted branch-wide.
