# Portfolio visual redesign — "Statement"

**Date:** 2026-08-13
**Status:** Approved design, ready for implementation planning
**Scope:** Complete visual replacement across all six surfaces. No backend changes.

## Goal

Replace the current visual world with one that is distinctive, warm, and unmistakably 2026, without losing a single feature — and close the WCAG 2.2 AA colour gap by construction rather than by patching.

Three problems drive this:

1. **The palette violates the AA bar PRODUCT.md commits to.** `gain #21c55d` measures 2.28:1 on white and fails every ground. `status-warning #ffc107` measures 1.63:1 under white toast text. `gray-500 #adb5bd` measures 2.07:1 and is used as a text colour in six places — four through the token, two hardcoded.
2. **Gain and red are perceptually mismatched.** Loss at 4.53:1 carries roughly twice the visual weight of gain at 2.28:1, so a loss looks louder than an equal gain. In a P&L application that is a visual thumb on the scale, and it contradicts Product Principle 1.
3. **The system has forked.** 100 hardcoded hex values sit in component scoped styles across 15 files — 38 distinct, with `#4b5563` alone appearing ten times — plus four orphaned Recharts-era colours in `portfolio-chart.vue` and a second palette file, `ui/constants/chart-colors.ts`. The Single-Source Rule is aspirational rather than enforced.

Mode is **Operate**: scanability, consistency, and the real usage scene outrank expression. Brand lives in precise details, not gestures.

## 1. The visual world

**Point of view:** a private-bank statement, not a trading terminal. Warm archival paper, deep ink, one metal accent. Nothing glows; things are printed.

### 1.1 Palette

All values are OKLCH-authored and verified against every ground. Ratios below are WCAG 2.x contrast.

**Grounds**

| token                     | oklch                   | hex       |
| ------------------------- | ----------------------- | --------- |
| `--color-paper`           | `oklch(0.985 0.006 85)` | `#fcfaf6` |
| `--color-surface`         | `oklch(1 0 0)`          | `#ffffff` |
| `--color-surface-sunken`  | `oklch(0.965 0.008 85)` | `#f6f3ed` |
| `--color-surface-hover`   | `oklch(0.975 0.007 85)` | `#f9f6f2` |
| `--color-hairline`        | `oklch(0.905 0.008 85)` | `#e2dfda` |
| `--color-hairline-strong` | `oklch(0.855 0.010 85)` | `#d2cfc8` |

**Foreground and signal**

| token                | oklch                    | hex       | on surface | on paper | on sunken |
| -------------------- | ------------------------ | --------- | ---------- | -------- | --------- |
| `--color-ink`        | `oklch(0.240 0.012 60)`  | `#241e1a` | 16.48      | 15.79    | 14.89     |
| `--color-ink-soft`   | `oklch(0.500 0.014 60)`  | `#69625b` | 6.02       | 5.77     | 5.44      |
| `--color-ink-faint`  | `oklch(0.620 0.012 60)`  | `#8c857f` | 3.65       | 3.50     | 3.30      |
| `--color-brass`      | `oklch(0.530 0.098 74)`  | `#8d621f` | 5.37       | 5.15     | 4.85      |
| `--color-brass-deep` | `oklch(0.460 0.098 74)`  | `#784e00` | 7.26       | 6.95     | 6.56      |
| `--color-gain`       | `oklch(0.520 0.115 152)` | `#287b46` | 5.22       | 5.00     | 4.71      |
| `--color-loss`       | `oklch(0.520 0.160 26)`  | `#b33834` | 5.98       | 5.72     | 5.40      |
| `--color-notice`     | `oklch(0.520 0.110 250)` | `#316ca5` | 5.49       | 5.26     | 4.96      |

**Washes** — badge fills, row tints, toast backgrounds. Each carries its own ink at AA.

| token                 | oklch                    | hex       | own ink on it |
| --------------------- | ------------------------ | --------- | ------------- |
| `--color-brass-wash`  | `oklch(0.955 0.020 74)`  | `#f8eee2` | 4.71          |
| `--color-gain-wash`   | `oklch(0.955 0.022 152)` | `#e6f5e9` | 4.61          |
| `--color-loss-wash`   | `oklch(0.955 0.022 26)`  | `#ffebe8` | 5.20          |
| `--color-notice-wash` | `oklch(0.955 0.022 250)` | `#e5f2ff` | 4.82          |

**White text on fill:** brass 5.37, brass-deep 7.26, gain 5.22, loss 5.98, notice 5.49, ink 16.48. All AA. This makes `text-white` on a gain-filled control legal, which it is not today at 2.28:1.

**Quantitative chart series** — six, hue-spread, all ≥3:1 on surface for WCAG 1.4.11. These govern line and bar charts, where a series is a measured quantity.

| token              | oklch                    | hex       | ratio |
| ------------------ | ------------------------ | --------- | ----- |
| `--color-series-1` | `oklch(0.530 0.098 74)`  | `#8d621f` | 5.37  |
| `--color-series-2` | `oklch(0.520 0.115 152)` | `#287b46` | 5.22  |
| `--color-series-3` | `oklch(0.520 0.110 250)` | `#316ca5` | 5.49  |
| `--color-series-4` | `oklch(0.520 0.160 26)`  | `#b33834` | 5.98  |
| `--color-series-5` | `oklch(0.520 0.120 310)` | `#7e539c` | 5.83  |
| `--color-series-6` | `oklch(0.580 0.070 200)` | `#42878b` | 4.15  |

**Categorical chart colours** — the ten-slot palette in `ui/constants/chart-colors.ts` is Okabe-Ito, chosen for colour-vision deficiency, and it **stays**. It does a job the quantitative ramp cannot: ten simultaneously distinguishable categories in an ETF pie. It moves into `theme.css` as `--color-cat-1` … `--color-cat-10` plus `--color-cat-others` so the second palette file stops being a fork; `chart-colors.ts` becomes `var()` reads.

Four of the ten fall below 3:1 on white — `#E69F00` 2.25, `#56B4E9` 2.31, `#F0E442` 1.32, `#999999` 2.85. Re-toning them would break the calibrated lightness relationships that make Okabe-Ito CVD-safe, so instead each slice gets a 1px `hairline-strong` stroke. The stroke carries the 1.4.11 boundary requirement, the existing text legend carries the label, and colour is never the sole carrier.

### 1.2 Palette rules

These are enforceable constraints, not guidance.

1. **Gain and loss are locked to `L = 0.520`.** Equal magnitudes carry equal visual weight. Neither may be re-toned independently.
2. **Brass is the only brand accent.** Nav active state, focus ring, primary action, active filter chip. Nothing else.
3. **Gain and loss are reserved for signed monetary movement.** They never decorate, never indicate non-financial success or failure.
4. **`ink-faint` is not body text.** At 3.65:1 it satisfies 1.4.11 non-text and AA large text (≥24px, or ≥18.66px bold) only. Its legitimate uses are chart axis ticks, disabled control glyphs, and decorative rules. **Every piece of small text — labels, captions, the build hash, stat-card labels, table meta — uses `ink-soft` at 6.02:1.** This rule is what prevents the current `gray-500` mistake recurring under a new name.
5. **State layers use `color-mix()`** against the base token. Hover and active states are never a second hardcoded colour.
6. **No component declares a colour.** Every value resolves to a token.

### 1.3 Typography

- `--font-sans: 'Instrument Sans Variable', system-ui, sans-serif` — all UI, labels, controls, tabular data. Weights 400–700.
- `--font-display: 'Instrument Serif', Georgia, serif` — page titles, section headings, card labels, the nav wordmark, and the Summary hero figure.
- `--font-mono` — unchanged.

Both faces ship self-hosted via `@fontsource-variable/instrument-sans` and `@fontsource/instrument-serif` (5.3.0), latin subset, preloaded, `font-display: swap`.

Every numeric cell and figure carries `font-variant-numeric: tabular-nums` so columns align and digits do not jitter during price-flash animation.

Fluid scale via `clamp()`, replacing per-breakpoint font-size overrides:

| token            | value                                        |
| ---------------- | -------------------------------------------- |
| `--text-display` | `clamp(2.5rem, 1.6rem + 3.6vw, 3.5rem)`      |
| `--text-title`   | `clamp(1.5rem, 1.3rem + 0.9vw, 1.875rem)`    |
| `--text-heading` | `clamp(1.125rem, 1.05rem + 0.35vw, 1.25rem)` |
| `--text-base`    | `0.9375rem`                                  |
| `--text-sm`      | `0.875rem`                                   |
| `--text-2xs`     | `0.8125rem` (unchanged)                      |

`text-wrap: balance` on headings, `text-wrap: pretty` on prose.

### 1.4 Material

Hairline-first, shadow-second — printed paper does not float.

- Cards: warm hairline plus a near-invisible tinted shadow. Only modals and popovers receive real elevation.
- `--radius-control` moves from `0.375rem` to `0.25rem`; `--radius-container` stays `0.5rem`. Crisper edges read as printed rather than app chrome.
- Shadows are warm-tinted: derived from `oklch(0.24 0.012 60 / α)` rather than neutral black.
- Active nav is a 2px brass rule beneath the item, not a filled pill.

### 1.5 Platform features adopted

`@theme static` OKLCH tokens generating every utility · `@container` queries on stat cards · `color-mix()` state layers · `:has()` for row and filter state · `@starting-style` with `transition-behavior: allow-discrete` for dialog enter and exit · Popover API for the quick-dates dropdown · View Transitions on route change · `text-wrap: balance` · `field-sizing: content` on calculator inputs · fluid `clamp()` type.

Tokens are authored so `light-dark()` can be introduced later without restructuring. Dark mode itself is out of scope.

## 2. The shared spine

Six surfaces are inconsistent because each hand-rolls its chrome. Today there are three page-header patterns, four stat-card implementations, two filter-chip implementations, and two container idioms. Each becomes one primitive.

**`app-shell`** — `paper` background, single `max-w-app` container replacing the mix of `mx-auto max-w-app px-3` and bespoke `*-container` classes. Nav gains a **Portfolio** wordmark in `--font-display` at the left; the build hash keeps its position at the right, restyled to `ink-soft`. Nav becomes sticky at all widths rather than only ≥992px.

**`page-header`** — display-font title, optional right slot for status or actions, optional filter row beneath. Absorbs `crud-layout`'s title and the four bare `<h2>` elements, and gives Summary the title it currently lacks.

**`stat-card`** — one primitive replacing all four implementations. Uppercase `ink-soft` label, tabular figure, optional delta and sparkline slots. **`@container`-driven**: sizing responds to the card's own width, so the same component renders 4-up at desk width and 2-up on a phone with no breakpoint and no second component.

**`filter-chip`** — one primitive for platform and ETF filters. Unselected is a hairline outline on surface. Selected is `brass-wash` fill with `brass` text and a `brass` border — state carried by fill _and_ border weight, not colour alone, satisfying 1.4.1. Minimum 44px target below 768px preserved.

**`data-table`** — keeps the `.mobile-cards-wrapper` path. Retires the duplicate `@media (max-width: 666px)` stacked-table CSS and the `@media (orientation: landscape)` override at line 357 that is its only route in. Desktop gains a sticky header, `tabular-nums` throughout, hairline row rules replacing striping, brass sort indicators, and `:has()`-driven row hover.

**`modal-shell`** — `@starting-style` plus `allow-discrete` enter and exit, removing the JS animation path. The `autofocus` attribute on `.modal-content` is load-bearing for the pixel harness and stays exactly where it is.

**Toasts** — the four semantic washes with ink text, replacing the current filled backgrounds. This fixes `toast-warning`'s white-on-`#ffc107` at 1.63:1.

**Forms** — brass focus rings, tokenized borders, `field-sizing: content` on calculator numerics.

**Charts** — Chart.js grid lines, ticks, and tooltips read the neutral tokens. Line and bar datasets read `--color-series-*`, removing `#8884d8`, `#ffc658`, `#82ca9d`, and `#ff7300` from `portfolio-chart.vue`. Pie datasets read `--color-cat-*` and gain the slice stroke.

## 3. The six surfaces

### 3.1 Summary (`/`)

The only surface whose information architecture changes.

Today's figures are promoted out of table row 1 into a statement band: total value set in `--font-display` at `--text-display`, today's change beside it in gain or loss, and a 30-day sparkline drawn from the data `usePortfolioChart` already derives. Beneath it a `stat-card` row — XIRR annual return, total profit, unrealized profit, earnings per month.

**Hard requirement:** the hero binds to the latest summary _by date_, never to `sortedItems[0]`. Sorting the table by XIRR must not change what the hero claims today's value is.

Below the band: the chart, then the full history table with all eight columns and infinite scroll intact.

No new endpoint. Every figure is already in the existing payload.

### 3.2 Instruments

Unified `page-header` with platform chips, period select, and active-only toggle in one filter row. The 734-line table keeps every column, both modals, the animated totals footer (desktop `tfoot` and mobile-footer slot), and the mobile card path — restyled onto the token ramp.

No per-instrument sparkline: the instruments endpoint returns a single change figure per period, not a series.

### 3.3 Transactions

`page-header` absorbs the `<h2>`. The four stat cards become the shared primitive.

The quick-dates dropdown moves to the **Popover API**, removing the `onClickOutside` handler, the `onKeyStroke('Escape')` handler, and the `isDropdownOpen` ref, and gaining top-layer rendering and light-dismiss natively. Date inputs, filters, and localStorage persistence are unchanged.

### 3.4 ETF Breakdown

`page-header` plus ETF and platform chips through the shared primitives. The total-value / unique-holdings / currency-split header becomes `stat-card`s. The three pie charts keep their grid and their Okabe-Ito hues, now token-backed and stroked.

The search input gains a real `<label>`; it is placeholder-only today, a 3.3.2 gap. Search, debounce, and the 616-line table are untouched.

### 3.5 Diversification

`page-header` preserving the save-status indicator and the click-to-refresh timestamp.

`allocation-table.vue` is 964 lines against the project's 300-line limit. The toolbar half — total-investment input, platform filter, optimize and buy-only toggles, action-display mode, and the add / remove / clear / load-portfolio / export / import actions — extracts to `allocation-toolbar.vue`. One clean split, not a rewrite. The calculation table, both config dialogs, stats, currency split, and three breakdown cards keep their behaviour exactly.

### 3.6 Calculator

`page-header`, form in a `surface` card with brass focus rings and `field-sizing: content` on numeric inputs, charts on the token ramp, the two XIRR stat cards through the primitive, year-by-year table restyled.

No outsider-facing copy: PRODUCT.md records that this surface serves the same single owner.

### 3.7 Preserved across all six

Every route, column, sort, filter, toggle, modal, dialog, localStorage key, query, refetch interval, infinite scroll, price-flash animation, number-roll transition, and all 44px touch targets.

The only deletions are the dead stacked-table CSS in `data-table.vue`, the dropdown JS the Popover API replaces, and the hardcoded hexes.

## 4. Migration

Baselines are re-recorded **per phase**, with the image diff reviewed as that phase's artifact. A single 41-image commit at the end would be unreviewable, which is the opposite of the deliberate re-recording PRODUCT.md requires.

**Phase 0 — the gate, written first.** `ui/tests/visual/palette.spec.ts` becomes a real contrast gate: read computed styles from live rendered routes, compute WCAG ratios, assert ≥4.5:1 for text and ≥3:1 for borders, focus rings, and chart strokes. Against the current palette it fails immediately on gain at 2.28:1 — that failure is the proof the gate works, and its output is Phase 0's artifact. No visual change, no baseline churn.

Because a gate that is _supposed_ to fail cannot be merged alone, Phase 0 and Phase 1 land as one merge: Phase 0 is a separate step with its own recorded evidence, not a separate green build. Main is never red.

**Phase 1 — the world.** `theme.css` rewritten to the OKLCH ramp. Fontsource faces installed and preloaded. `base.css` updated. The inline loader `<style>` in `ui/index.html` retokenized — it hardcodes `#fafafa` and `#4361ee`, so without this the first paint flashes the old world before Vue mounts. `DESIGN.md` is **replaced**, not edited, so later phases have an authority to follow. Full 41-baseline re-record, reviewed as one set: colour and type change everywhere, structure nowhere.

**Phase 2 — the spine.** app-shell, nav, page-header, stat-card, filter-chip, data-table, modal-shell, toasts, form controls, chart tokens. The hardcoded hexes and dead CSS die here.

**Phase 3 — Summary.** Alone, because it is the only information-architecture change.

**Phase 4 — Instruments and Transactions.**

**Phase 5 — ETF Breakdown, Diversification, and Calculator**, including the `allocation-toolbar.vue` extraction.

**Phase 6 — sweep.** knip, the `add-button-text="New InstrumentDto"` copy fix in `instruments-view.vue:5`, final audit pass.

## 5. Verification

Every phase gates on all of:

- `npm run lint-format`
- `npm test` — 52 test files
- The Playwright visual suite at three viewports, `maxDiffPixels: 0`
- The Phase 0 contrast gate
- `npm run check-unused`

Nine test files assert on style classes — including `instrument-table`, `allocation-table`, `etf-breakdown-header`, `instruments-view`, `portfolio-summary`, and `diversification-stats`. Each phase reviews the affected files rather than discovering breakage in CI.

Two more assert on colour by value and are expected to break at a known phase:

- `palette.spec.ts` hardcodes `rgb(33, 197, 93)` and `rgb(220, 53, 69)`; rewritten in Phase 0.
- `portfolio-chart.test.ts` asserts all four Recharts hexes as `borderColor` values; updated in Phase 2 to assert token references.

## 6. Costs and risks

**Webfonts are not free.** The current stack is `Avenir, Helvetica, Arial` at zero bytes. Two latin-subset variable faces add roughly 40–60KB woff2 against a 17KB gzip CSS budget. Preloaded with `font-display: swap`. This is the largest performance cost of the redesign and it buys the entire typographic identity.

**The serif hero is the one unproven call.** Instrument Serif at `--text-display` rendering a euro figure is the most expressive move here and the most likely to disappoint in practice. It is bound to `--font-display` and used in exactly one place; reverting is a one-line change reviewed against a screenshot.

**Favicon.** PRODUCT.md records that no logo or wordmark exists and forbids fabricating assets. The nav wordmark is typography, not a logo. The favicon is still Vite's default; it is replaced by a `P` set in Instrument Serif, `brass` on `paper`, with no enclosing shape or container. A letterform is not an invented mark.

**Scope.** This is one coherent visual replacement, but it is too large for a single implementation plan. Expect the writing-plans step to produce a plan per phase group — the phase boundaries here are the decomposition seams, each independently reviewable and each ending on green gates.

**Phase 1 reds every baseline at once.** This is expected and is the point of the phase, but it means Phase 1's review is a careful visual read of 41 images rather than a diff scan.

## 7. Out of scope

- Dark mode. Tokens are structured so `light-dark()` can be added later; nothing more.
- Any backend change.
- Per-instrument sparklines — no series data exists at that endpoint.
- The `/api/vehicle/info` endpoint, which stays deliberately headless.
