# Visual Harness Gaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every visual-regression capture read from a fixture instead of the live dev database, then delete the masks that currently hide the cells Phases 5–6 will migrate.

**Architecture:** Phases 0–2 left six captures reading `/diversification` and `/calculator` from the developer's database, and a 24-entry global mask list that predates the stubbing work. Once the last two routes are stubbed, `.build-info-text` is the only selector in `VOLATILE_SELECTORS` still pointing at non-deterministic content — and that is one `page.route` away from deterministic too. So the fix for the mask gap is not per-route mask lists; it is deleting the mask lists. Each task re-records only the baselines its own change moves, and proves stability by running the suite twice.

**Tech Stack:** Playwright 1.5x (`toHaveScreenshot`, `maxDiffPixels: 0`), Vue 3.5, TypeScript, Vite 8, Chart.js.

## Global Constraints

- Branch: `feature/1662-visual-harness-gaps`. Never commit to `main`.
- **NEVER add code comments.** No `//`, no `/* */`, no `/** */`. Hard project rule.
- Commit subjects: uppercase imperative, ≤50 chars, no `feat:`/`fix:`/`chore:` prefixes.
- No AI attribution in commits or PRs.
- File naming: kebab-case, no `-new`/`-improved`/`-refactored` suffixes.
- Fixture data is invented (`TST*` symbols) but sized to the live shape it replaces — the convention every existing fixture in `ui/tests/visual/` follows.
- Every fixture date is a fixed literal in the past. Nothing may be derived from "today".
- The app must be running on `http://localhost:61234` with the backend on `8081` for any `playwright` command.
- After UI-adjacent changes run `npm run lint-format`, then `git checkout -- ui/models/generated/domain-models.ts` (eslint `--fix` strips that file's `/* eslint-disable */` header). Never commit that file.
- `npm run lint-format` already exits non-zero from pre-existing knip findings. The gate is "no new findings", not "exit 0".
- Re-recording is `npx playwright test --grep "<pattern>" --update-snapshots`. Verification is the same command without `--update-snapshots`, run **twice**.

## File Structure

**Created:**

| File                                         | Responsibility                                                                             |
| -------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `ui/tests/visual/calculator-fixture.ts`      | `/api/calculator` — 139 rolling-XIRR cash flows plus median/average/total                  |
| `ui/tests/visual/diversification-fixture.ts` | `/api/diversification/{available-etfs,calculate,config}` plus the instruments it layers on |
| `ui/tests/visual/windows-fixture.ts`         | `/api/portfolio-summary/{xirr,annual}-windows` — six periods each                          |
| `ui/tests/visual/build-info-fixture.ts`      | `/api/build-info` — a fixed hash and timestamp, installed on every capture                 |

**Modified:**

| File                                 | Change                                                                                                                            |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| `ui/tests/visual/routes.spec.ts`     | Stub the last two routes; install the build-info stub for every capture                                                           |
| `ui/tests/visual/states.spec.ts`     | Stub `/diversification` behind the two config modals and the windows modals; install the build-info stub; drop the mask arguments |
| `ui/tests/visual/volatile.ts`        | Deleted — both lists become empty                                                                                                 |
| `ui/tests/visual/settle.ts`          | Drop `masks()`; `freezeBehindOverlay` collapses into `freeze`                                                                     |
| `docs/superpowers/baseline/**`       | Re-recorded PNGs                                                                                                                  |
| `docs/superpowers/baseline/index.md` | Rewrite the volatility and known-limits sections to match reality                                                                 |

---

### Task 1: `/calculator` fixture

**Files:**

- Create: `ui/tests/visual/calculator-fixture.ts`
- Modify: `ui/tests/visual/routes.spec.ts`

**Interfaces:**

- Consumes: `apiRoute`, `RouteStub` from `./stub`; `API_ENDPOINTS` from `../../constants/api`.
- Produces: `stubCalculator: RouteStub`.

The route looked inert because `calculator.vue` imports no service. The fetch is one level down: `use-calculator.ts:44` sets `queryFn: utilityService.getCalculationResult`, which `utility-service.ts:11` resolves to `API_ENDPOINTS.CALCULATOR`. Its response drives three things the baseline renders — `#annualReturnRate` (set from `median` on a fresh load, and **not** in `VOLATILE_SELECTORS`), `#initialWorth` (set from `total`), and the bar chart (plots `cashFlows` directly, gaining a bar per new cash flow).

Measured live payload, which the fixture mirrors: 139 cash flows on a 28-day cadence from `2016-01-08` to `2026-08-07`, amounts from `-74.31` to `+25.92`, `median` `20.144654250487978`, `average` `16.213664972192674`, `total` `170938.4656135397`. Full float precision matters: `median` lands in an `<input>` verbatim, so a round number like `12.5` would not render the digit string the app actually shows.

- [ ] **Step 1: Write the fixture**

Create `ui/tests/visual/calculator-fixture.ts`:

```ts
import { type CalculationResult } from '../../models/generated/domain-models'
import { API_ENDPOINTS } from '../../constants/api'
import { apiRoute, type RouteStub } from './stub'

const CASH_FLOW_COUNT = 139
const CASH_FLOW_INTERVAL_DAYS = 28
const FIRST_CASH_FLOW_MS = Date.UTC(2016, 0, 8)
const DAY_MS = 86400000

const cashFlowDate = (index: number): string =>
  new Date(FIRST_CASH_FLOW_MS + index * CASH_FLOW_INTERVAL_DAYS * DAY_MS).toISOString().slice(0, 10)

const cashFlowAmount = (index: number): number =>
  22.5 - 96 * Math.exp(-index / 4.5) + 2.4 * Math.sin(index / 3.1)

const RESULT: CalculationResult = {
  cashFlows: Array.from({ length: CASH_FLOW_COUNT }, (_, index) => ({
    date: cashFlowDate(index),
    amount: cashFlowAmount(index),
  })),
  median: 19.847302516384927,
  average: 15.632948170255413,
  total: 164272.83910456281,
}

export const stubCalculator: RouteStub = async page => {
  await page.route(apiRoute(API_ENDPOINTS.CALCULATOR), route => route.fulfill({ json: RESULT }))
}
```

- [ ] **Step 2: Wire it into the route table**

In `ui/tests/visual/routes.spec.ts`, import `stubCalculator` from `./calculator-fixture` and give the `/calculator` entry `stub: stubCalculator`.

- [ ] **Step 3: Run the capture and watch it fail**

Run: `npx playwright test --grep "route calculator"`
Expected: 3 failed. The baselines were recorded against the database, so a fixture-backed render must differ. A pass here means the stub is not matching.

- [ ] **Step 4: Re-record**

Run: `npx playwright test --grep "route calculator" --update-snapshots`

- [ ] **Step 5: Inspect the re-recorded desktop PNG**

Open `docs/superpowers/baseline/route-calculator-desktop.png`. Confirm: `Annual Return Rate` reads `19.847302516384927`, the bar chart spans `2016-01-08` to a 2026 label, its bars start deeply negative and settle just above `+20%`, and the "Year-by-Year Summary" table is still a solid magenta block (Task 5 removes that mask, not this one).

- [ ] **Step 6: Verify twice**

Run `npx playwright test --grep "route calculator"` twice.
Expected: 3 passed, both times.

- [ ] **Step 7: Commit**

```bash
git add ui/tests/visual/calculator-fixture.ts ui/tests/visual/routes.spec.ts docs/superpowers/baseline
git commit -m "Serve the calculator route from a fixture"
```

---

### Task 2: `/diversification` fixture

**Files:**

- Create: `ui/tests/visual/diversification-fixture.ts`
- Modify: `ui/tests/visual/routes.spec.ts`, `ui/tests/visual/states.spec.ts`

**Interfaces:**

- Consumes: `stubInstruments` from `./instruments-fixture`; `CachedState` from `../../components/diversification/types`; `Currency`, `DiversificationCalculatorResponseDto`, `EtfDetailDto` from the generated models.
- Produces: `stubDiversification: RouteStub`.

Three endpoints, all reached from `diversification-calculator.vue`: `:148` polls `getAvailableEtfs` on a `refetchInterval`, `:427` loads the persisted `DiversificationConfig` row, `:322` posts to `/calculate`. `:204` also calls `instrumentsService.getAll()` on mount, so the stub layers `stubInstruments` underneath — without it the platform filter buttons and the Current column are database-backed.

This is the one fixture that deliberately does **not** reproduce today's live render. The dev database has no persisted config (`/api/diversification/config` answers `204`), so the live page is a single blank "Select ETF" row and the committed baseline gates almost nothing — no allocation rows, no stats cards, no breakdown tables. Those are exactly the components Phase 3 migrates. The fixture supplies a five-ETF config so the baseline covers them.

Stubbing `/config` also intercepts the `PUT` that `use-diversification-config.ts:16` fires, which is why the capture can no longer write to the developer's database.

- [ ] **Step 1: Write the fixture**

Create `ui/tests/visual/diversification-fixture.ts` with twelve `EtfDetailDto` entries (the live endpoint returns thirty; only allocated rows render, and every capture leaves the `<select>` closed, so twelve is enough to fill the dropdown without inventing eighteen unused funds). Cover the branches the components have: a `null` `ter`, a `null` `currentPrice`, EUR/USD/GBP `fundCurrency` so the Fund Currency card renders three rows, and a non-ASCII name.

```ts
import {
  Currency,
  type DiversificationCalculatorResponseDto,
  type EtfDetailDto,
} from '../../models/generated/domain-models'
import { API_ENDPOINTS } from '../../constants/api'
import { type CachedState } from '../../components/diversification/types'
import { stubInstruments } from './instruments-fixture'
import { apiRoute, type RouteStub } from './stub'

const AVAILABLE_ETFS: EtfDetailDto[] = [
  {
    instrumentId: 101,
    symbol: 'TSTWLD:GER:EUR',
    name: 'Test World Equity Index Tracker',
    allocation: 0,
    ter: 0.6,
    annualReturn: 0.1521,
    currentPrice: 14.62,
    fundCurrency: Currency.EUR,
  },
]
```

Keep the remaining eleven entries in the same shape. The five instrument ids referenced by `CONFIG.allocations` must exist in this list.

```ts
const CONFIG: CachedState = {
  allocations: [
    { instrumentId: 101, value: 32.5 },
    { instrumentId: 103, value: 24 },
    { instrumentId: 108, value: 18.5 },
    { instrumentId: 109, value: 15 },
    { instrumentId: 110, value: 10 },
  ],
  inputMode: 'percentage',
  selectedPlatforms: ['LHV', 'TRADING212'],
  optimizeEnabled: false,
  buyOnlyEnabled: false,
  totalInvestment: 5000,
  actionDisplayMode: 'units',
}

export const stubDiversification: RouteStub = async page => {
  await stubInstruments(page)
  await page.route(apiRoute(`${API_ENDPOINTS.DIVERSIFICATION}/available-etfs`), route =>
    route.fulfill({ json: AVAILABLE_ETFS })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.DIVERSIFICATION}/calculate`), route =>
    route.fulfill({ json: CALCULATION })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.DIVERSIFICATION}/config`), route =>
    route.fulfill({ json: CONFIG })
  )
}
```

`CALCULATION` is a `DiversificationCalculatorResponseDto` with ten holdings, ten sectors, ten countries, a `concentration` block, and `weightedTer` / `weightedAnnualReturn` / `totalUniqueHoldings`. Include a `null` `ticker`, a `null` `countryCode` rendered as "Other", and a holding name long enough to truncate.

- [ ] **Step 2: Wire it into the route table**

In `routes.spec.ts`, import `stubDiversification` and give the `/diversification` entry `stub: stubDiversification`.

- [ ] **Step 3: Wire it into the two config modals**

In `states.spec.ts`, the `config-export` and `config-import` entries in `MODALS` carry no `stub` and therefore render the dialog over a database-backed page. Add `stub: stubDiversification` to both.

- [ ] **Step 4: Run the captures and watch them fail**

Run: `npx playwright test --grep "route diversification|modal config-"`
Expected: 3 route failures plus 4 modal failures.

- [ ] **Step 5: Re-record**

Run: `npx playwright test --grep "route diversification|modal config-" --update-snapshots`

- [ ] **Step 6: Inspect the re-recorded desktop PNG**

Open `docs/superpowers/baseline/route-diversification-desktop.png`. Confirm five allocation rows with Buy/Sell actions, four stats cards, the Fund Currency card listing EUR/USD/GBP, and the Top Holdings / Sectors / Countries tables. If the page is a single blank "Select ETF" row, the `/config` stub is not matching.

- [ ] **Step 7: Verify twice**

Run `npx playwright test --grep "route diversification|modal config-"` twice.
Expected: 7 passed, both times.

- [ ] **Step 8: Commit**

```bash
git add ui/tests/visual/diversification-fixture.ts ui/tests/visual/routes.spec.ts \
  ui/tests/visual/states.spec.ts docs/superpowers/baseline
git commit -m "Serve the diversification route from a fixture"
```

---

### Task 3: `/api/build-info` fixture

**Files:**

- Create: `ui/tests/visual/build-info-fixture.ts`
- Modify: `ui/tests/visual/routes.spec.ts`, `ui/tests/visual/states.spec.ts`

**Interfaces:**

- Produces: `stubBuildInfo(page: Page): Promise<void>`.

`nav-bar.vue:17` renders `buildInfo.hash.substring(0, 7)` and `formatDate(buildInfo.time)`. The live endpoint answers `{"hash":"unknown","time":"<now>"}` — the timestamp is the moment of the request. `.build-info-text` is masked, but index.md already records the hole: the mask hides pixels, not the box, so a version string of a different rendered width moves every baseline at once. It is also the only entry in `VOLATILE_SELECTORS` that survives Tasks 1 and 2, so Task 5 cannot delete the list until this is stubbed.

This task must produce **zero** re-recorded baselines. The element is still masked, so replacing its content changes no visible pixel. A diff here means the stubbed string renders at a different width, which is the very failure mode this task removes — re-record in that case and say so in the commit.

- [ ] **Step 1: Write the fixture**

Create `ui/tests/visual/build-info-fixture.ts`:

```ts
import { type Page } from '@playwright/test'
import { API_ENDPOINTS } from '../../constants/api'
import { apiRoute } from './stub'

const BUILD_INFO = {
  hash: 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678',
  time: '2026-08-07T09:15:00Z',
}

export const stubBuildInfo = (page: Page): Promise<void> =>
  page.route(apiRoute(API_ENDPOINTS.BUILD_INFO), route => route.fulfill({ json: BUILD_INFO }))
```

- [ ] **Step 2: Install it on every capture**

Add to `routes.spec.ts` and to `states.spec.ts`, above the first `test`:

```ts
test.beforeEach(async ({ page }) => {
  await stubBuildInfo(page)
})
```

A `beforeEach` is safe here in a way the route fixtures are not: no capture registers a competing handler for `/api/build-info`, so there is no most-recently-registered-wins interaction to get wrong. In `states.spec.ts` the existing `test.beforeEach(({}, testInfo) => test.skip(...))` hooks live inside `describe` blocks; add the new hook at file scope so it also covers the `desktop states` block.

- [ ] **Step 3: Run the whole suite**

Run: `npx playwright test`
Expected: 42 passed, zero re-records. If any capture fails, open its diff before re-recording.

- [ ] **Step 4: Commit**

```bash
git add ui/tests/visual/build-info-fixture.ts ui/tests/visual/routes.spec.ts ui/tests/visual/states.spec.ts
git commit -m "Serve build info from a fixture"
```

---

### Task 4: XIRR and annual windows fixture

**Files:**

- Create: `ui/tests/visual/windows-fixture.ts`
- Modify: `ui/tests/visual/states.spec.ts`

**Interfaces:**

- Produces: `stubWindows(page: Page): Promise<void>`.

`xirr-windows-modal.vue:83` and `annual-windows-modal.vue:83` call `portfolioSummaryService.getXirrWindows(...)` / `getAnnualWindows(...)`. `stubInstruments` is anchored to `/\/api\/instruments(\?|$)/` and structurally cannot match those paths, so both dialogs are live-data-backed even though the page behind them is not.

The live response is `{ windows: [...] }` with six periods — `1M`, `3M`, `6M`, `1Y`, `2Y`, `3Y` — and each row's `fromDate` is computed **relative to today**, so the dates change daily. They survive today only because `#xirrWindowsModal tbody td.text-end` masks both the value column and the Since column. That mask also hides `returnClass(row.xirr)`, the gain/loss colour Phases 5–6 migrate.

- [ ] **Step 1: Write the fixture**

Create `ui/tests/visual/windows-fixture.ts`:

```ts
import { type Page } from '@playwright/test'
import { API_ENDPOINTS } from '../../constants/api'
import { apiRoute } from './stub'

const PERIODS = [
  { period: '1M', fromDate: '2026-07-07' },
  { period: '3M', fromDate: '2026-05-07' },
  { period: '6M', fromDate: '2026-02-07' },
  { period: '1Y', fromDate: '2025-08-07' },
  { period: '2Y', fromDate: '2024-08-07' },
  { period: '3Y', fromDate: null },
]

const XIRR = [0.689492, -0.085556, 0.6839, 0.329316, 0.259209, 0.246688]
const ANNUAL = [0.252707, 0.385203, -0.034291, 0.187415, 0.204862, 0.198337]

export const stubWindows = async (page: Page): Promise<void> => {
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_XIRR_WINDOWS), route =>
    route.fulfill({
      json: { windows: PERIODS.map((p, i) => ({ ...p, xirr: XIRR[i] })) },
    })
  )
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_ANNUAL_WINDOWS), route =>
    route.fulfill({
      json: { windows: PERIODS.map((p, i) => ({ ...p, annualReturn: ANNUAL[i] })) },
    })
  )
}
```

One negative value per dialog is deliberate: it is the only coverage of `returnClass`'s loss branch, and Task 5 unmasks the cell that renders it. The `null` `fromDate` covers the `?? '—'` fallback documented in the modal's own footnote.

- [ ] **Step 2: Install it on the two windows captures**

In `states.spec.ts`, change the `xirr-windows` and `annual-windows` entries' `stub` to a function that installs both `stubInstruments` and `stubWindows`.

- [ ] **Step 3: Run the captures and watch them fail**

Run: `npx playwright test --grep "modal (xirr|annual)-windows"`
Expected: 4 failed.

- [ ] **Step 4: Re-record and verify twice**

Run `npx playwright test --grep "modal (xirr|annual)-windows" --update-snapshots`, then the same command without the flag, twice.
Expected: 4 passed, both times.

- [ ] **Step 5: Commit**

```bash
git add ui/tests/visual/windows-fixture.ts ui/tests/visual/states.spec.ts docs/superpowers/baseline
git commit -m "Serve the XIRR window dialogs from a fixture"
```

---

### Task 5: Delete the masks

**Files:**

- Delete: `ui/tests/visual/volatile.ts`
- Modify: `ui/tests/visual/settle.ts`, `ui/tests/visual/routes.spec.ts`, `ui/tests/visual/states.spec.ts`

Every entry in `VOLATILE_SELECTORS` now points at fixture-backed content. `.build-info-text` went in Task 3; `#initialWorth`, `.stat-value` and `[data-testid="year-summary-table"]` in Task 1; `.total-value`, `.metric-value`, `.currency-value` and `[data-testid="currency-split-pct"]` are rendered by `/diversification`, `/instruments` and `/etf-breakdown`, all stubbed; the `.profit-column` family and the `td[data-label="…"]` selectors come from `instrumentColumns` and `transactionColumns` in `ui/config/table-columns.ts`; the two `24h Change` first-row selectors and `[data-testid="summary-chart"]` belong to `/`. `MODAL_VOLATILE_SELECTORS` clears the same way: the windows cells went in Task 4, and the logo dialog's title and body come from `stubEtfBreakdown`.

This is the task that pays for the other four. Gain/loss colour and numeric-cell typography — what Phases 5–6 migrate through `--color-gain` — are currently pixel-verified only through the summary table's non-first-row 24h Change cells.

Expect large re-records and treat every one as a review item: a mask coming off should reveal fixture text, never a value that could have come from the database.

- [ ] **Step 1: Delete the mask plumbing**

Delete `ui/tests/visual/volatile.ts`. In `settle.ts`, delete `masks`, delete the `VOLATILE_SELECTORS` import, and replace `freezeBehindOverlay` with a call to `waitForScrollHeightToSettle` followed by `freeze` — the `evaluateAll` visibility-hiding pass has nothing left to hide. Keep `FREEZE_STYLES`: the `.currency-value { overflow: hidden }` rule and the Monaco indent-guide rule are not masks.

- [ ] **Step 2: Drop the mask arguments from every capture**

In `routes.spec.ts`, `toHaveScreenshot` keeps only `{ fullPage: true }`. In `states.spec.ts`, drop `masks(page, MODAL_VOLATILE_SELECTORS)` and `masks(page)` from all six modal captures, `modal confirm`, the four toasts, and the three `state-*` captures, along with the now-unused imports.

- [ ] **Step 3: Run the whole suite and watch it fail**

Run: `npx playwright test`
Expected: a large number of failures. A capture that passes unchanged carried no mask.

- [ ] **Step 4: Re-record**

Run: `npx playwright test --update-snapshots`

- [ ] **Step 5: Inspect the three highest-risk PNGs**

- `route-calculator-desktop.png` — the Year-by-Year Summary table must now render 30 rows of numbers where a magenta block used to be.
- `route-summary-desktop.png` — `[data-testid="summary-chart"]` was masked, so this is the first baseline to pixel-gate a Chart.js canvas. Confirm the chart renders complete, with no half-drawn animation frame.
- `modal-xirr-windows-desktop.png` — the value column must show percentages, one of them in the loss colour.

- [ ] **Step 6: Verify three times**

Run `npx playwright test` three times, not two. Unmasking puts two Chart.js canvases on `/calculator`, one on `/`, and any on `/etf-breakdown` under a zero-pixel gate for the first time; canvas animation settles on wall-clock time, and the settle gate only measures page height. A single flaky capture here invalidates the gate for every later phase.
Expected: 42 passed, all three times. If one capture flakes, do not re-record it — find whether the movement is the chart animation (raise the settle requirement) or genuine data (a missing stub).

- [ ] **Step 7: Check types and unused exports**

```bash
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
npm run check-unused
```

Expected: no new findings. `knip` should not report `volatile.ts` — it is deleted, not orphaned.

- [ ] **Step 8: Commit**

```bash
git add -A ui/tests/visual docs/superpowers/baseline
git commit -m "Pixel-gate the cells the masks were hiding"
```

---

### Task 6: Documentation and PR

**Files:**

- Modify: `docs/superpowers/baseline/index.md`

- [ ] **Step 1: Rewrite the volatility section**

`index.md`'s "Volatility handling" section opens by describing `VOLATILE_SELECTORS` and the `visibility: hidden` pass behind overlays. Both are gone. Replace with a statement that every capture is fixture-backed and nothing is masked, keeping the per-fixture tables that follow.

- [ ] **Step 2: Add the two new fixtures to the fixture list**

The intro paragraph lists four fixture files; it now needs `calculator-fixture.ts`, `diversification-fixture.ts`, `windows-fixture.ts` and `build-info-fixture.ts`, with the same "changing the fixture re-records N baselines" note each existing entry carries.

- [ ] **Step 3: Retire the resolved known limits**

Delete or rewrite these bullets, all now false: "`VOLATILE_SELECTORS` has gaps", "The two windows dialogs are still live-data-backed", "`/diversification` and `/calculator` both still read the live database", "`/api/build-info` and `/api/enums` are still live on every route" (only `/api/enums` remains), and the trailing sentence of the "Today's row" bullet. Keep "Masking hides pixels, not boxes" only if a mask survives; it does not.

- [ ] **Step 4: Record the new limits**

Add: `/diversification`'s baseline renders a config the dev database does not have, so it is the one fixture that does not reproduce the live page; and the Chart.js canvases are now gated on animation completing within the settle window.

- [ ] **Step 5: Commit and open the PR**

```bash
git add docs/superpowers/baseline/index.md
git commit -m "Document the fixture-backed visual harness"
git push -u origin feature/1662-visual-harness-gaps
```

`git push` and `gh` need `dangerouslyDisableSandbox: true` in this environment.

```bash
gh pr create --title "Close the visual harness gaps" --body "$(cat <<'EOF'
## Summary

- Stubs the last two live-database routes, `/calculator` and `/diversification`, plus `/api/build-info` and the two XIRR window dialogs
- Deletes `VOLATILE_SELECTORS` and `MODAL_VOLATILE_SELECTORS` entirely: with every capture fixture-backed there is nothing left that needs masking
- Gain/loss colour, numeric-cell typography and the Year-by-Year Summary table are pixel-gated for the first time — the cells Phases 5-6 migrate through `--color-gain`
- `/diversification`'s fixture supplies a five-ETF config the dev database does not have, so the baseline covers the allocation table, stats cards and breakdown tables Phase 3 migrates

These are the two entry criteria Phase 3 inherited from phases 0-2.

## Test plan

- [ ] `npx playwright test` passes 42/42 three times consecutively
- [ ] `npm run lint-format` reports no new findings
- [ ] `npm run check-unused` reports no new findings
EOF
)"
```
