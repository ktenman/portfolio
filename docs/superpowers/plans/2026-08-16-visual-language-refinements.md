# Visual Language Refinements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the six visual-language changes the user approved from the `impeccable critique` A/B comparison — honest money display, a real page headline, one flat button system, a greyscale-separable chart ramp, an off-white surface token, and a `--text-label` type token.

**Architecture:** All changes are frontend-only and land in three layers: the token layer (`ui/styles/theme.css`), the formatter layer (`ui/utils/formatters.ts`, `ui/utils/instrument-formatters.ts`), and the component layer. Behaviour-bearing changes (signs, chart ramp) get Vitest coverage first; token and CSS changes are gated by `npm run lint-format`, the existing Playwright `palette.spec.ts` contrast suite, and a final baseline re-record.

**Tech Stack:** Vue 3.5 SFCs (composition API, `<script setup>`), TypeScript 6 strict, Tailwind CSS v4 (`tw:` prefix stripped, `@theme static` in `ui/styles/theme.css`), Chart.js via vue-chartjs, Vitest for unit tests, Playwright for the pixel + contrast harness.

## Global Constraints

- **No code comments.** Not `//`, not `/* */`, not JSDoc. Self-documenting names only.
- **No AI attribution** in commits or PR bodies. No `Co-Authored-By: Claude`, no "Generated with Claude Code".
- **Commit subjects:** uppercase imperative verb, no `feat:`/`fix:`/`chore:` prefixes, max 50 characters, no body unless the _why_ is invisible in the diff.
- **Branch:** all work lands on `feature/visual-language-refinements`, branched from `main`. Never commit to `main`.
- **`ui/models/generated/domain-models.ts` is auto-generated** — never edit it. `npm run lint-format` mutates it on every run (it strips the generated file's eslint-disable header); `git checkout -- ui/models/generated/domain-models.ts` after each lint run.
- **After every UI change run both:** `npm run lint-format` and `npm test -- --run`.
- **`npm run lint-format` exits 1 locally** on untracked `.claude/worktrees/` copies that knip scans. Only knip findings that name files under `ui/` count as real.
- **Vue components stay under 200 lines**; kebab-case filenames; no `-new`/`-improved`/`-refactored` suffixes.
- **Prefer `const`**; TypeScript strict; no `any`.
- **U+2212 MINUS SIGN (`−`) is the negative-delta glyph**, not ASCII hyphen. `+` is U+002B. Both are tabular-width in Geist, which is why they belong in right-aligned money columns.
- **Do not touch `CHART_COLORS`, `DONUT_COLORS`, `OTHERS_COLOR`, or `DONUT_OTHERS_COLOR`** — `ui/constants/chart-colors.test.ts` asserts `CHART_COLORS` holds exactly two lightness levels. The four-step ramp is a new, separate constant.
- **Do not change the Chart.js legend config** in `ui/components/portfolio/portfolio-chart.vue` (lines 120-132). The user explicitly asked to keep it, including the ring-vs-dot difference caused by `fill: true` on the first dataset.
- **Out of scope, explicitly rejected by the user:** brass overload on filter chips (critique item 4) and the tablet horizontal-overflow fix (critique item 7). Do not touch `ui/components/shared/platform-filter.vue` or table overflow behaviour.

---

## File Structure

**Modified — token layer**

- `ui/styles/theme.css` — the single palette and type scale. Gains `--text-label: 0.75rem`; `--color-surface` changes value.

**Modified — formatter layer**

- `ui/utils/formatters.ts` — gains `formatSignedCurrency` and `formatSignedPercent`; `formatPriceChange` becomes signed.
- `ui/utils/instrument-formatters.ts` — loses `formatProfit` (moves to `formatters.ts` as `formatSignedCurrency`); keeps `calculatePortfolioWeight`.

**Modified — constants**

- `ui/constants/chart-colors.ts` — gains `PORTFOLIO_SERIES_COLORS`.

**Modified — styles**

- `ui/styles/components/buttons.css` — `.btn-primary` and `.btn-danger` fills flatten.
- `ui/styles/components/controls.css`, `ui/styles/components/surfaces.css` — `0.75rem` → `var(--text-label)`.

**Modified — components (13 SFCs)**

- `ui/components/portfolio-summary.vue` — headline `<h1>`, right-aligned summary columns, destructive confirm, signed 24h percent.
- `ui/components/portfolio/range-change-header.vue` — delegates signing to the formatters.
- `ui/components/portfolio/portfolio-chart.vue` — consumes `PORTFOLIO_SERIES_COLORS`.
- `ui/components/shared/data-table.vue` — one CSS rule so `text-right!` reaches the flex `<th>` label.
- `ui/config/table-columns.ts` — `text-right!` on numeric columns.
- `ui/components/instruments/instrument-table.vue` — `formatProfit` → `formatSignedCurrency`, `0.75rem` → token.
- `ui/components/calculator.vue` — destructive confirm, `0.75rem` → token.
- `ui/components/diversification/allocation-card.vue`, `allocation-table.vue` — `toFixed` euro strings → `formatCurrencyWithSign`, `0.75rem` → token.
- `ui/components/nav-bar.vue`, `ui/components/shared/stat-card.vue`, `ui/components/transactions/transactions-view.vue`, `ui/components/transactions/transaction-table.vue`, `ui/components/instruments/instruments-view.vue`, `ui/components/etf/etf-breakdown.vue`, `ui/components/etf/etf-breakdown-table.vue`, `ui/components/diversification/diversification-calculator.vue`, `ui/components/diversification/breakdown-card.vue` — `0.75rem` → token only.

**Modified — tests**

- `ui/utils/formatters.test.ts` — gains `formatSignedCurrency`, `formatSignedPercent` suites; `formatPriceChange` expectations update.
- `ui/utils/instrument-formatters.test.ts` — `formatProfit` suite removed.
- `ui/constants/chart-colors.test.ts` — gains the portfolio-series-ramp suite.
- `docs/superpowers/baseline/*.png` — 40 baselines re-recorded in the final task.

**Created**

- Nothing. Every change edits an existing file.

---

### Task 0: Branch

- [ ] **Step 1: Confirm a clean tree and branch from main**

```bash
git status --short
git switch -c feature/visual-language-refinements
```

`Modelfile` and `qwen38.tmpl` are pre-existing untracked files unrelated to this work. Leave them untracked; never `git add -A`.

---

### Task 1: Token layer — `--text-label` and the off-white surface

Two `@theme static` edits plus 28 mechanical replacements. No behaviour changes; the gate is that zero raw `0.75rem` font-sizes remain and the Playwright contrast suite still passes.

`--color-surface` moves from pure white to a hair of warmth so cards read as paper stock rather than a light leak. Measured cost: ink-on-surface contrast 16.48 → 16.25, ink-soft 6.02 → 5.93, ink-faint 3.65 → 3.60. Every ratio that passed before still passes.

**Files:**

- Modify: `ui/styles/theme.css:20` and `ui/styles/theme.css:80`
- Modify (28 `font-size` declarations across 15 files, listed in Step 3)

**Interfaces:**

- Produces: CSS custom property `--text-label` with value `0.75rem`, usable as `var(--text-label)` in any stylesheet and as the Tailwind utility `text-label`.

- [ ] **Step 1: Write the failing check**

There is no CSS unit-test harness in this repo and adding one for two tokens is not worth it. The runnable red/green check is a grep that must go from 28 hits to 0:

```bash
grep -rn "font-size: 0.75rem" ui --include='*.vue' --include='*.css'
```

- [ ] **Step 2: Run it to see it fail**

Run the grep above. Expected: 28 matching lines across 15 files. Record the count — it must be 0 at the end of this task.

- [ ] **Step 3: Add both tokens to `ui/styles/theme.css`**

Change line 20 from `--color-surface: oklch(1 0 0);` to:

```css
--color-surface: oklch(0.995 0.004 85);
```

Insert `--text-label` as the new first entry of the type scale, immediately above `--text-2xs` on line 80:

```css
--text-label: 0.75rem;
--text-2xs: 0.8125rem;
```

- [ ] **Step 4: Replace all 28 declarations**

Every one of these is exactly `font-size: 0.75rem;` and becomes exactly `font-size: var(--text-label);`. Do not touch `padding`, `gap`, `margin`, or `border-radius` values of `0.75rem` — only `font-size`.

```
ui/styles/components/surfaces.css:43
ui/styles/components/controls.css:80
ui/components/nav-bar.vue:132
ui/components/calculator.vue:153
ui/components/shared/stat-card.vue:18
ui/components/diversification/diversification-calculator.vue:442
ui/components/diversification/diversification-calculator.vue:467
ui/components/diversification/breakdown-card.vue:41
ui/components/diversification/allocation-card.vue:257
ui/components/diversification/allocation-card.vue:282
ui/components/diversification/allocation-table.vue:674
ui/components/diversification/allocation-table.vue:696
ui/components/diversification/allocation-table.vue:713
ui/components/diversification/allocation-table.vue:749
ui/components/diversification/allocation-table.vue:772
ui/components/diversification/allocation-table.vue:782
ui/components/diversification/allocation-table.vue:812
ui/components/transactions/transactions-view.vue:235
ui/components/transactions/transactions-view.vue:290
ui/components/transactions/transactions-view.vue:300
ui/components/transactions/transactions-view.vue:305
ui/components/transactions/transaction-table.vue:261
ui/components/instruments/instruments-view.vue:249
ui/components/instruments/instrument-table.vue:576
ui/components/instruments/instrument-table.vue:651
ui/components/etf/etf-breakdown-table.vue:503
ui/components/etf/etf-breakdown-table.vue:568
ui/components/etf/etf-breakdown.vue:544
```

Line numbers are pre-edit. Since every replacement is the same length in lines, they stay valid throughout. A `sed` sweep is acceptable here because the match string is unambiguous:

```bash
grep -rl "font-size: 0.75rem" ui --include='*.vue' --include='*.css' \
  | xargs sed -i '' 's/font-size: 0\.75rem;/font-size: var(--text-label);/g'
```

- [ ] **Step 5: Run the check to verify it passes**

```bash
grep -rn "font-size: 0.75rem" ui --include='*.vue' --include='*.css'
```

Expected: no output, exit 1.

- [ ] **Step 6: Run lint and unit tests**

```bash
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
npm test -- --run
```

Expected: type check and ESLint clean; all Vitest suites pass. Ignore knip findings that name paths under `.claude/worktrees/`.

- [ ] **Step 7: Run the contrast suite**

```bash
npx playwright test ui/tests/visual/palette.spec.ts
```

Expected: all 6 tests pass (3 routes × text-contrast + focus-ring). This is the real gate on the surface colour change. If a text failure appears, it will name the sample string, the flattened colour, the flattened background, and the ratio — fix by darkening the offending foreground token, not by reverting `--color-surface`.

- [ ] **Step 8: Commit**

```bash
git add ui/styles/theme.css ui/styles/components ui/components
git commit -m "Add text-label token and warm the surface"
```

---

### Task 2: Chart series ramp

The four portfolio series currently sit at identical lightness (`CHART_COLORS[0]`, `[1]`, `[3]`, `[5]` are all `L=0.55`). Their neighbouring greyscale contrast ratios are **1.04, 1.01, 1.07** — one flat block in print, in greyscale, and under deuteranopia. Replacing them with a single-hue lightness ramp gives **1.63, 1.67, 1.62**.

The ramp is compressed relative to the mockup the user saw (`0.34/0.52/0.66/0.80`), whose lightest step measured only 1.78:1 against paper — too faint to see. The shipped ramp `0.32/0.44/0.56/0.68` keeps the same even greyscale steps while lifting the faintest line to 2.74:1.

`CHART_COLORS` stays untouched: it is a categorical 16-slice wheel with a test pinning it to two lightness levels. The portfolio chart's four series are named, ordered and permanent — a different thing that deserves its own constant.

**Files:**

- Modify: `ui/constants/chart-colors.ts`
- Modify: `ui/components/portfolio/portfolio-chart.vue:45-72`
- Test: `ui/constants/chart-colors.test.ts`

**Interfaces:**

- Produces: `export const PORTFOLIO_SERIES_COLORS: string[]` — exactly 4 entries, index order `[Total Value, Total Profit, XIRR Annual Return, Earnings Per Month]`, each an `oklch(L C H)` string parseable by the existing `parseRampColor` regex `/^oklch\(([\d.]+) ([\d.]+) ([\d.]+)\)$/`.
- Consumes: `contrastRatio`, `isInSrgbGamut`, `luminanceFromOklch`, `type Oklch` from `../tests/contrast`; `parseRampColor` (module-local in the test file).

- [ ] **Step 1: Write the failing test**

Append to `ui/constants/chart-colors.test.ts`, after the `the breakdown donut palette` describe block. Add `PORTFOLIO_SERIES_COLORS` to the existing import on line 2.

```ts
describe('the portfolio series ramp', () => {
  const ramp = PORTFOLIO_SERIES_COLORS.map(parseRampColor)

  it('carries one entry per line on the portfolio chart', () => {
    expect(PORTFOLIO_SERIES_COLORS).toHaveLength(4)
  })

  it('separates every neighbouring pair in greyscale so the lines survive print', () => {
    const steps = ramp
      .slice(1)
      .map((color, index) =>
        contrastRatio(luminanceFromOklch(color), luminanceFromOklch(ramp[index]))
      )
    expect(Math.min(...steps)).toBeGreaterThanOrEqual(1.4)
  })

  it('darkens monotonically so the ramp reads as an order, not a scatter', () => {
    const ascending = ramp.every((color, index) => index === 0 || color.l > ramp[index - 1].l)
    expect(ascending).toEqual(true)
  })

  it('renders every entry inside the sRGB gamut', () => {
    expect(ramp.filter(color => !isInSrgbGamut(color))).toEqual([])
  })

  it('keeps even the faintest line visible against the paper it sits on', () => {
    const paper = luminanceFromOklch(PAPER)
    const ratios = ramp.map(color => contrastRatio(luminanceFromOklch(color), paper))
    expect(Math.min(...ratios)).toBeGreaterThanOrEqual(2.5)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm test -- --run ui/constants/chart-colors.test.ts
```

Expected: FAIL — `PORTFOLIO_SERIES_COLORS` is not exported from `./chart-colors`.

- [ ] **Step 3: Add the constant**

Append to `ui/constants/chart-colors.ts`, after `DONUT_OTHERS_COLOR` and before `withAlpha`:

```ts
export const PORTFOLIO_SERIES_COLORS = [
  'oklch(0.32 0.07 250)',
  'oklch(0.44 0.10 250)',
  'oklch(0.56 0.11 250)',
  'oklch(0.68 0.10 250)',
]
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm test -- --run ui/constants/chart-colors.test.ts
```

Expected: PASS, including the untouched `CHART_COLORS` and `DONUT_COLORS` suites.

- [ ] **Step 5: Wire the chart to the ramp**

In `ui/components/portfolio/portfolio-chart.vue`, change the import on line 13:

```ts
import { PORTFOLIO_SERIES_COLORS, withAlpha } from '../../constants/chart-colors'
```

Then replace every colour reference in the four datasets (lines 45-72). `CHART_COLORS[0] → PORTFOLIO_SERIES_COLORS[0]`, `[1] → [1]`, `[3] → [2]`, `[5] → [3]`:

```ts
      {
        label: isCompact.value ? 'Value' : 'Total Value',
        borderColor: PORTFOLIO_SERIES_COLORS[0],
        backgroundColor: withAlpha(PORTFOLIO_SERIES_COLORS[0], 0.08),
        pointHoverBackgroundColor: PORTFOLIO_SERIES_COLORS[0],
        fill: true,
        data: props.data.totalValues,
        yAxisID: 'y',
      },
      {
        label: isCompact.value ? 'Profit' : 'Total Profit',
        borderColor: PORTFOLIO_SERIES_COLORS[1],
        backgroundColor: PORTFOLIO_SERIES_COLORS[1],
        pointHoverBackgroundColor: PORTFOLIO_SERIES_COLORS[1],
        data: props.data.profitValues,
        yAxisID: 'y',
      },
      {
        label: isCompact.value ? 'XIRR' : 'XIRR Annual Return',
        borderColor: PORTFOLIO_SERIES_COLORS[2],
        backgroundColor: PORTFOLIO_SERIES_COLORS[2],
        pointHoverBackgroundColor: PORTFOLIO_SERIES_COLORS[2],
        data: props.data.xirrValues,
        yAxisID: 'y1',
      },
      {
        label: isCompact.value ? 'EPM' : 'Earnings Per Month',
        borderColor: PORTFOLIO_SERIES_COLORS[3],
        backgroundColor: PORTFOLIO_SERIES_COLORS[3],
        pointHoverBackgroundColor: PORTFOLIO_SERIES_COLORS[3],
        data: props.data.earningsValues,
        yAxisID: 'y',
      },
```

Leave `chartOptions` completely alone — the legend block on lines 120-132 must not change.

- [ ] **Step 6: Run lint and the full unit suite**

```bash
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
npm test -- --run
```

Expected: clean. `ui/components/portfolio/portfolio-chart.test.ts` must still pass — if it asserts on specific colour strings, update those assertions to the new ramp values rather than reverting.

- [ ] **Step 7: Commit**

```bash
git add ui/constants/chart-colors.ts ui/constants/chart-colors.test.ts ui/components/portfolio/portfolio-chart.vue
git commit -m "Separate portfolio series by lightness"
```

---

### Task 3: One button system, and destructive means red

Two problems collapse into one task. First, `.btn-primary` and `.btn-danger` paint 135° gradients while `.dialog-btn.primary` and `.dialog-btn.danger` paint flat fills — two button systems, one app. Second, the two genuinely destructive confirmations both request `confirmClass: 'btn-primary'`, which maps to `.dialog-btn.primary` — **graphite grey**. "Delete all summary data and recalculate from scratch" currently looks exactly like "OK".

Flattening also makes these buttons _visible to the contrast suite_ for the first time: a gradient leaves `getComputedStyle(el).backgroundColor` as `rgba(0,0,0,0)`, so `palette.spec.ts` walks past it to the parent. Measured white-on-flat ratios all clear AA: brass 5.37, brass-deep 7.26, loss 5.98, loss-deep 8.08.

**Files:**

- Modify: `ui/styles/components/buttons.css:52-64` and `:84-91`
- Modify: `ui/components/portfolio-summary.vue:248`
- Modify: `ui/components/calculator.vue:102`

**Interfaces:**

- Consumes: `--color-signal-indigo`, `--color-signal-indigo-deep`, `--color-loss`, `--color-loss-deep` from `ui/styles/theme.css`.
- Produces: nothing new. `.dialog-btn.danger` already exists in `ui/styles/components/controls.css` and is already flat red; `ui/components/shared/confirm-dialog.vue:24` already maps `btn-danger` to it.

- [ ] **Step 1: Write the failing test**

The pixel gate cannot see this. Recorded finding: `maxDiffPixels: 0` with `threshold` unset (default 0.2) is blind to colour-only changes, and gradient-to-flat plus grey-to-red are both colour-only. The assertion has to be structural.

Append to `ui/components/shared/confirm-dialog.test.ts`, inside the existing top-level `describe`:

```ts
it('should paint the affirmative button red when the caller asks for a destructive confirm', () => {
  const wrapper = createWrapper({ modelValue: true, confirmClass: 'btn-danger' })

  const affirmative = wrapper.find('[data-testid="confirmDialogConfirmButton"]')
  expect(affirmative.classes()).toContain('danger')
})
```

Verify the `data-testid` against `ui/components/shared/confirm-dialog.vue` before running; if the affirmative button carries a different testid, use the one in the source. Do not add a testid to the component for the test's benefit.

- [ ] **Step 2: Run test to verify it fails or passes**

```bash
npm test -- --run ui/components/shared/confirm-dialog.test.ts
```

This one may pass immediately — `confirm-dialog.test.ts:70` already exercises `confirmClass: 'btn-danger'`. If it passes, that is fine: it pins the contract that Step 3's call-site change depends on. Do not weaken it to force a red.

- [ ] **Step 3: Flatten both button fills**

In `ui/styles/components/buttons.css`, replace lines 52-64:

```css
.btn-primary {
  color: var(--color-white);
  background: var(--color-signal-indigo);
}

.btn-primary:hover:not(:disabled) {
  background: var(--color-signal-indigo-deep);
}
```

and replace lines 84-91:

```css
.btn-danger:not(.btn-ghost) {
  color: var(--color-white);
  background: var(--color-loss);
}

.btn-danger:not(.btn-ghost):hover:not(:disabled) {
  background: var(--color-loss-deep);
}
```

Leave `.btn-danger:not(.btn-ghost):active:not(:disabled)` (lines 93-95) and every `.btn-ghost` rule untouched.

- [ ] **Step 4: Mark the two destructive confirmations**

`ui/components/portfolio-summary.vue:248`, inside `handleRecalculate`:

```ts
    confirmClass: 'btn-danger',
```

`ui/components/calculator.vue:102`, inside `handleReset`:

```ts
    confirmClass: 'btn-danger',
```

Leave the `'btn-primary'` default in `ui/composables/use-confirm.ts:60` and `ui/components/shared/confirm-dialog.vue:54` alone — non-destructive confirmations should still default to the neutral affirmative.

- [ ] **Step 5: Run the tests**

```bash
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
npm test -- --run
npx playwright test ui/tests/visual/palette.spec.ts
```

Expected: Vitest green; contrast suite green. The contrast suite now inspects these fills for the first time, so a new failure here is a real finding, not flake — if one appears, swap the resting fill to the `-deep` variant (brass-deep 7.26, loss-deep 8.08) rather than reverting the flattening.

- [ ] **Step 6: Commit**

```bash
git add ui/styles/components/buttons.css ui/components/portfolio-summary.vue ui/components/calculator.vue ui/components/shared/confirm-dialog.test.ts
git commit -m "Flatten button fills and redden destructive confirms"
```

---

### Task 4: Honest money — signs

Money in this app conflates two concepts. **Magnitudes** (price, value, invested, commission) never take a sign. **Deltas** (profit, unrealized profit, price change, 24h change, range change) are signed both ways. Today a single misnamed helper, `formatCurrencyWithSign`, serves both and _strips_ the sign — so two call sites re-add signs by hand, each with a different glyph, and `formatPriceChange` renders a loss identically to a gain.

`ui/utils/instrument-formatters.ts` already holds the signed helper the codebase needs (`formatProfit`) — it just never emits `+`. Rather than add a third formatter, move it to `ui/utils/formatters.ts` alongside its siblings under an honest name and give it the `+`.

`formatCurrencyWithSign` keeps its misleading name and its 20 call sites. Renaming it changes no pixels and would touch six files for nothing.

**Files:**

- Modify: `ui/utils/formatters.ts`
- Modify: `ui/utils/instrument-formatters.ts`
- Modify: `ui/components/instruments/instrument-table.vue` (8 call sites + import on line 347)
- Modify: `ui/components/portfolio/range-change-header.vue`
- Modify: `ui/components/portfolio-summary.vue:186-198`
- Modify: `ui/components/diversification/allocation-card.vue:42,114,127,140`
- Modify: `ui/components/diversification/allocation-table.vue:266,409,494`
- Test: `ui/utils/formatters.test.ts`, `ui/utils/instrument-formatters.test.ts`, `ui/components/portfolio/range-change-header.test.ts`

**Interfaces:**

- Produces: `formatSignedCurrency(value: number, currency: string | undefined): string` — `+€100.00` / `−€100.00` / `€0.00`. Zero is unsigned because zero is neither a gain nor a loss, matching `getGainLossClass(0)` which already returns `''`.
- Produces: `formatSignedPercent(value: number): string` — `+17.16%` / `−8.46%` / `0.00%`. Same zero rule, two decimals.
- Removes: `formatProfit` from `ui/utils/instrument-formatters.ts`. `calculatePortfolioWeight` stays and keeps its signature `(instrumentValue: number, totalValue: number) => string`.

- [ ] **Step 1: Write the failing tests**

Add to `ui/utils/formatters.test.ts`. Import `formatSignedCurrency` and `formatSignedPercent` alongside the existing imports.

```ts
describe('formatSignedCurrency', () => {
  it('should prefix a gain with a plus', () => {
    expect(formatSignedCurrency(100, 'EUR')).toBe('+€100.00')
    expect(formatSignedCurrency(1234567.89, 'EUR')).toBe('+€1,234,567.89')
  })

  it('should prefix a loss with a typographic minus', () => {
    expect(formatSignedCurrency(-100, 'EUR')).toBe('−€100.00')
    expect(formatSignedCurrency(-1234567.89, 'EUR')).toBe('−€1,234,567.89')
  })

  it('should leave a flat value unsigned', () => {
    expect(formatSignedCurrency(0, 'EUR')).toBe('€0.00')
  })

  it('should use the currency it is given', () => {
    expect(formatSignedCurrency(50, 'USD')).toBe('+$50.00')
    expect(formatSignedCurrency(-50, 'GBP')).toBe('−£50.00')
  })

  it('should default to euro when the currency is undefined', () => {
    expect(formatSignedCurrency(75, undefined)).toBe('+€75.00')
  })

  it('should round to two decimals', () => {
    expect(formatSignedCurrency(123.456, 'EUR')).toBe('+€123.46')
  })
})

describe('formatSignedPercent', () => {
  it('should prefix a gain with a plus', () => {
    expect(formatSignedPercent(17.16)).toBe('+17.16%')
  })

  it('should prefix a loss with a typographic minus', () => {
    expect(formatSignedPercent(-8.46)).toBe('−8.46%')
  })

  it('should leave a flat value unsigned', () => {
    expect(formatSignedPercent(0)).toBe('0.00%')
  })
})
```

Replace the two `formatPriceChange` expectations at `ui/utils/formatters.test.ts:375` and `:379`. The existing `'€1.23 / 4.56%'` case becomes signed, and add a loss case:

```ts
it('should sign both halves of a gain', () => {
  expect(formatPriceChange(item)).toBe('+€1.23 / +4.56%')
})

it('should sign both halves of a loss', () => {
  expect(formatPriceChange({ ...item, priceChangeAmount: -1.23, priceChangePercent: -4.56 })).toBe(
    '−€1.23 / −4.56%'
  )
})
```

Update `ui/components/portfolio/range-change-header.test.ts` — the gain case currently expects `'+€25,429.00 (+17.16%)'`, which stays correct, and the loss case expects `'−€8,431.20 (−8.46%)'`, which also stays correct. Add the flat case's text, which changes from `'+€0.00 (+0.00%)'` to unsigned:

```ts
it('should dont colour a flat range', () => {
  const wrapper = mount(RangeChangeHeader, {
    props: { amount: 0, percent: 0 },
  })

  const change = wrapper.find('.range-change')
  expect(change.classes()).toEqual(['range-change'])
  expect(change.text()).toBe('€0.00 (0.00%)')
})
```

Delete the whole `describe('formatProfit', ...)` block from `ui/utils/instrument-formatters.test.ts` (lines 5-44) and drop `formatProfit` from its import on line 2. The `calculatePortfolioWeight` describe stays untouched.

- [ ] **Step 2: Run tests to verify they fail**

```bash
npm test -- --run ui/utils/formatters.test.ts ui/components/portfolio/range-change-header.test.ts
```

Expected: FAIL — `formatSignedCurrency` and `formatSignedPercent` are not exported; `formatPriceChange` returns `'€1.23 / 4.56%'`.

- [ ] **Step 3: Add both formatters**

In `ui/utils/formatters.ts`, insert after `formatCurrencyWithSign` (which ends on line 91):

```ts
const signFor = (value: number): string => {
  if (value > 0) return '+'
  if (value < 0) return '−'
  return ''
}

export const formatSignedCurrency = (value: number, currency: string | undefined): string =>
  `${signFor(value)}${formatCurrencyWithSign(value, currency)}`

export const formatSignedPercent = (value: number): string =>
  `${signFor(value)}${Math.abs(value).toFixed(2)}%`
```

`formatCurrencyWithSign` already applies `Math.abs` and the currency symbol, so `signFor` is the only missing piece.

- [ ] **Step 4: Make `formatPriceChange` honest**

Replace the body of `formatPriceChange` at `ui/utils/formatters.ts:232-245`:

```ts
export const formatPriceChange = (item: InstrumentDto): string => {
  const amount = item.priceChangeAmount
  const percent = item.priceChangePercent

  if (amount === null || amount === undefined || percent === null || percent === undefined) {
    return '-'
  }

  return `${formatSignedCurrency(amount, item.baseCurrency || 'EUR')} / ${formatSignedPercent(percent)}`
}
```

- [ ] **Step 5: Remove `formatProfit` and repoint its callers**

`ui/utils/instrument-formatters.ts` loses its first export and its import, leaving:

```ts
export function calculatePortfolioWeight(instrumentValue: number, totalValue: number): string {
  if (totalValue === 0) return '0.00%'
  const weight = (instrumentValue / totalValue) * 100
  return `${weight.toFixed(2)}%`
}
```

In `ui/components/instruments/instrument-table.vue`, split the import on line 347:

```ts
import { calculatePortfolioWeight } from '../../utils/instrument-formatters'
```

and add `formatSignedCurrency` to the existing `../../utils/formatters` import in the same block. Then rename the 8 template call sites — lines 32, 42, 127, 133, 180, 192, 293, 306 — from `formatProfit(` to `formatSignedCurrency(`. The arguments do not change.

- [ ] **Step 6: Delegate signing in `range-change-header.vue`**

Replace lines 7-18 of `ui/components/portfolio/range-change-header.vue`:

```ts
import { formatSignedCurrency, formatSignedPercent, getGainLossClass } from '../../utils/formatters'

const props = defineProps<{
  amount: number
  percent: number
}>()

const label = computed(
  () => `${formatSignedCurrency(props.amount, 'EUR')} (${formatSignedPercent(props.percent)})`
)
```

- [ ] **Step 7: Sign the 24h percent in `portfolio-summary.vue`**

Replace `format24hChangePercentage` at lines 186-198:

```ts
const format24hChangePercentage = (summary: PortfolioSummaryDto) => {
  const change = summary.totalProfitChange24h
  if (change === null || Math.abs(change) <= 0.01) {
    return ''
  }
  const previousValue = summary.totalValue - change
  if (previousValue <= 0) {
    return ''
  }
  return `(${formatSignedPercent((change / previousValue) * 100)})`
}
```

Add `formatSignedPercent` to the `../utils/formatters` import block at lines 110-115.

- [ ] **Step 8: Route the seven hand-built euro strings through the formatter**

These bypass every formatter, so they lose thousands separators (`€173204.51`) and hardcode `€` regardless of currency. All seven are magnitudes, so they take the unsigned `formatCurrencyWithSign`.

`ui/components/diversification/allocation-card.vue:42`:

```html
<span class="metric-value">{{ formatCurrencyWithSign(allocation.currentValue ?? 0, 'EUR') }}</span>
```

`ui/components/diversification/allocation-card.vue:114`:

```ts
return price === null || price === undefined ? '-' : formatCurrencyWithSign(price, 'EUR')
```

`ui/components/diversification/allocation-card.vue:127`:

```ts
return formatCurrencyWithSign(amount, 'EUR')
```

`ui/components/diversification/allocation-card.vue:140`:

```ts
return formatCurrencyWithSign(unused, 'EUR')
```

`ui/components/diversification/allocation-table.vue:266`:

```html
{{ formatCurrencyWithSign(allocation.currentValue ?? 0, 'EUR') }}
```

`ui/components/diversification/allocation-table.vue:409`:

```html
<span class="total-value text-gray-600!">{{ formatCurrencyWithSign(totalUnused, 'EUR') }}</span>
```

`ui/components/diversification/allocation-table.vue:494`:

```ts
const formatEtfPrice = (value: number | null) =>
  value === null ? '-' : formatCurrencyWithSign(value, 'EUR')
```

Add `formatCurrencyWithSign` to each file's `../../utils/formatters` import. Note that `allocation-card.vue:42` gains two decimals — it was the only site using `.toFixed(0)`, and `allocation-table.vue:266` renders the same `currentValue` field with two. That inconsistency is the defect.

Leave the percent `toFixed` calls at `allocation-card.vue:145` and `allocation-table.vue:268,309,403` alone. They are one-decimal allocation percentages, not money, and they are internally consistent.

- [ ] **Step 9: Run tests to verify they pass**

```bash
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
npm test -- --run
```

Expected: PASS. `ui/components/diversification/allocation-card.test.ts` and `allocation-table.test.ts` may assert on the old unseparated strings — update those expectations to the separated form (`€173,204.51`), which is the intended change.

- [ ] **Step 10: Commit**

```bash
git add ui/utils ui/components/instruments/instrument-table.vue ui/components/portfolio/range-change-header.vue ui/components/portfolio/range-change-header.test.ts ui/components/portfolio-summary.vue ui/components/diversification
git commit -m "Sign money deltas and drop hand-built euro strings"
```

---

### Task 5: Honest money — alignment

Numbers in every table are left-aligned, so decimal points do not line up and column scanning fails. `body` already sets `font-variant-numeric: tabular-nums`, so right-aligning is all that is missing.

No new API is needed. `ColumnDefinition.class` already flows to both the `<th>` (`ui/components/shared/data-table.vue:66`) and the `<td>` (line 97), `text-right!` is already an established token in this file, and the mobile-card rule `.table td.text-right\! { justify-content: flex-end; }` (line 307) already exists. One CSS rule is missing because `.th-content` is a `space-between` flex container, which pins the header label left regardless of `text-align`.

**Files:**

- Modify: `ui/components/shared/data-table.vue` (scoped style, after `.th-content` at line 321)
- Modify: `ui/config/table-columns.ts`
- Modify: `ui/components/portfolio-summary.vue:200-225`

**Interfaces:**

- Consumes: `ColumnDefinition` from `./shared/data-table.vue` — unchanged. `{ key, label, formatter?, class?, hideOnMobile?, sortable?, sortKey? }`.
- Produces: nothing new.

- [ ] **Step 1: Write the failing test**

Append to `ui/components/shared/data-table.test.ts`, inside the existing top-level `describe`:

```ts
it('should carry a column class onto both the header and the body cell', () => {
  const wrapper = mount(DataTable, {
    props: {
      items: [{ price: 12 }],
      columns: [{ key: 'price', label: 'Price', class: 'text-right!' }],
    },
  })

  expect(wrapper.find('thead th').classes()).toContain('text-right!')
  expect(wrapper.find('tbody td').classes()).toContain('text-right!')
})
```

Match the mount options to the file's existing helper — if `data-table.test.ts` already has a `createWrapper` or a shared `mount` config, use it rather than a bare `mount`.

- [ ] **Step 2: Run test to verify it fails or passes**

```bash
npm test -- --run ui/components/shared/data-table.test.ts
```

This pins the mechanism the rest of the task relies on. It will likely pass on the first run because `data-table.vue` already forwards `column.class` to both elements — that is the finding that made this task one CSS rule instead of a new API. Do not weaken it to force a red.

- [ ] **Step 3: Let the header label follow `text-right!`**

In `ui/components/shared/data-table.vue`, insert immediately after the `.th-content` rule that ends on line 326:

```css
.table th.text-right\! .th-content {
  justify-content: flex-end;
}
```

- [ ] **Step 4: Right-align the numeric columns**

`ui/config/table-columns.ts` — every column except `instrument` in `instrumentColumns`:

```ts
export const instrumentColumns: ColumnDefinition[] = [
  { key: 'instrument', label: 'Instrument', sortKey: 'name' },
  { key: 'quantity', label: 'Quantity', formatter: formatQuantity, class: 'text-right!' },
  {
    key: 'currentPrice',
    label: 'Price',
    formatter: formatCurrency,
    class: 'current-price-column text-right!',
  },
  { key: 'currentValue', label: 'Value', formatter: formatCurrency, class: 'text-right!' },
  {
    key: 'totalInvestment',
    label: 'Invested',
    formatter: formatCurrency,
    class: 'hidden! md:table-cell! text-right!',
    hideOnMobile: true,
  },
  { key: 'profit', label: 'Profit', formatter: formatCurrency, class: 'profit-column text-right!' },
  {
    key: 'unrealizedProfit',
    label: 'Unrealized',
    formatter: formatCurrency,
    class: 'unrealized-column text-right!',
  },
  {
    key: 'priceChange',
    label: '1D',
    class: 'hidden! lg:table-cell! price-change-column text-right!',
    hideOnMobile: true,
    sortKey: 'priceChangeAmount',
  },
  {
    key: 'xirr',
    label: 'XIRR',
    formatter: formatPercentageFromDecimal,
    class: 'text-right!',
  },
  {
    key: 'xirrAnnualReturn',
    label: 'Annual',
    formatter: formatPercentageFromDecimal,
    class: 'hidden! xl:table-cell! text-right!',
    hideOnMobile: true,
  },
  {
    key: 'portfolioWeight',
    label: 'Weight',
    class: 'hidden! xl:table-cell! weight-column text-right!',
    hideOnMobile: true,
    sortKey: 'currentValue',
  },
  { key: 'ter', label: 'TER', class: 'hidden! xl:table-cell! text-right!', hideOnMobile: true },
]
```

and the numeric columns in `transactionColumns` — `date` and `instrumentId` stay left:

```ts
export const transactionColumns: ColumnDefinition[] = [
  { key: 'transactionDate', label: 'Date', formatter: formatDate },
  { key: 'instrumentId', label: 'Instrument' },
  { key: 'quantityInfo', label: 'Quantity', class: 'text-right!' },
  { key: 'price', label: 'Price', formatter: formatCurrency, class: 'text-right!' },
  { key: 'amount', label: 'Amount', class: 'text-right!' },
  { key: 'profit', label: 'Profit', class: 'text-right!' },
  {
    key: 'averageCost',
    label: 'Average Cost',
    formatter: formatCurrency,
    class: 'hidden! sm:table-cell! text-right!',
    hideOnMobile: true,
  },
]
```

- [ ] **Step 5: Right-align the summary columns**

`ui/components/portfolio-summary.vue:200-225` — every column except `date`:

```ts
const summaryColumns: ColumnDefinition[] = [
  { key: 'date', label: 'Date', formatter: formatDate },
  {
    key: 'xirrAnnualReturn',
    label: 'XIRR Annual Return',
    formatter: formatPercentageFromDecimal,
    class: 'text-right!',
  },
  {
    key: 'earningsPerDay',
    label: 'Earnings Per Day',
    formatter: formatCurrencyWithSymbol,
    class: 'hidden! md:table-cell! text-right!',
    hideOnMobile: true,
  },
  {
    key: 'earningsPerMonth',
    label: 'Earnings Per Month',
    formatter: formatCurrencyWithSymbol,
    class: 'text-right!',
  },
  {
    key: 'unrealizedProfit',
    label: 'Unrealized Profit',
    formatter: formatCurrencyWithSymbol,
    class: 'hidden! md:table-cell! text-right!',
    hideOnMobile: true,
  },
  {
    key: 'totalProfit',
    label: 'Total Profit',
    formatter: formatCurrencyWithSymbol,
    class: 'text-right!',
  },
  {
    key: 'totalProfitChange24h',
    label: '24h Change',
    formatter: format24hChange,
    class: 'text-right!',
  },
  {
    key: 'totalValue',
    label: 'Total Value',
    formatter: formatCurrencyWithSymbol,
    class: 'text-right!',
  },
]
```

- [ ] **Step 6: Run the tests**

```bash
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
npm test -- --run
```

Expected: PASS.

- [ ] **Step 7: Eyeball the three tables at each viewport**

```bash
npm run dev
```

Open `/`, `/instruments`, `/transactions` at 390px, 768px and 1440px. Confirm: decimal points line up in every numeric column; header labels sit above their numbers, not opposite them; sort arrows stay adjacent to the label rather than being pushed to the far edge; mobile cards render label-left / value-right. `instrument-table.vue` overrides most cells with `#cell-*` slots whose contents are flex rows — check that those slots did not pick up unwanted alignment.

- [ ] **Step 8: Commit**

```bash
git add ui/components/shared/data-table.vue ui/components/shared/data-table.test.ts ui/config/table-columns.ts ui/components/portfolio-summary.vue
git commit -m "Right-align numeric table columns"
```

---

### Task 6: Page headline

The main portfolio screen has no `<h1>` — the document opens on a toolbar, a filter row and a chart, and the single most important number in the app is only readable off the chart's y-axis. The range change already exists as a component but floats as an absolutely-positioned overlay pinned to the chart's top-left corner, which is exactly where a headline belongs.

Promote the total to an `<h1>`, move `range-change-header` out of the overlay and under it, and state the as-of date so a stale figure is visible as stale.

**Files:**

- Modify: `ui/components/portfolio-summary.vue` (template lines 46-57, script, scoped style lines 262-267)

**Interfaces:**

- Consumes: `reversedSummaries` (already destructured at line 144) — newest-first, derived from `sortSummariesByDateAsc(...).reverse()` in `ui/composables/use-portfolio-summary-query.ts:102`. Element type `PortfolioSummaryDto` with `date: string` and `totalValue: number`.
- Consumes: `rangeChange` (line 143) with `{ changeAmount: number, changePercent: number }`.
- Consumes: `formatCurrencyWithSymbol` and `formatDate` — both already imported at lines 111-112.

- [ ] **Step 1: Write the failing test**

`ui/components/portfolio-summary.test.ts` tests logic without mounting the SFC, and mounting it would need vue-query, the router and eight stubs. Add the computed's rule as a logic test instead, in the same style as the file's existing suites:

```ts
describe('headline', () => {
  const latestOf = (summaries: { date: string; totalValue: number }[]) => summaries[0] ?? null

  it('should read the headline total off the newest summary', () => {
    const latest = latestOf([
      { date: '2026-08-16', totalValue: 173204.51 },
      { date: '2026-08-15', totalValue: 171980.22 },
    ])

    expect(formatCurrencyWithSymbol(latest?.totalValue)).toBe('€173,204.51')
  })

  it('should dont render a headline when no summaries have loaded', () => {
    expect(latestOf([])).toBe(null)
  })
})
```

`formatCurrencyWithSymbol` is already imported at the top of this test file.

- [ ] **Step 2: Run test to verify it passes**

```bash
npm test -- --run ui/components/portfolio-summary.test.ts
```

Expected: PASS. This pins the newest-first ordering contract the template depends on; the visible headline itself is verified by the baseline re-record in Task 7.

- [ ] **Step 3: Add the computed**

In `ui/components/portfolio-summary.vue`, after `showRecalculationMessage` on line 177:

```ts
const latestSummary = computed(() => reversedSummaries.value[0] ?? null)
```

- [ ] **Step 4: Render the headline and un-float the range change**

Replace the `chart-frame` block at lines 46-57:

```html
<header v-if="latestSummary" class="portfolio-headline">
  <h1>{{ formatCurrencyWithSymbol(latestSummary.totalValue) }}</h1>
  <div class="headline-meta">
    <range-change-header
      v-if="rangeChange"
      :amount="rangeChange.changeAmount"
      :percent="rangeChange.changePercent"
    />
    <span class="headline-asof">as of {{ formatDate(latestSummary.date) }}</span>
  </div>
</header>

<div class="chart-frame">
  <portfolio-chart :key="chartKey" :data="processedChartData" />
  <div v-if="isRangeLoading" class="chart-veil">
    <loading-spinner message="Loading chart" />
  </div>
</div>
```

- [ ] **Step 5: Replace the overlay style with the headline style**

In the scoped block, delete `.chart-overlay-change` (lines 262-267) and add:

```css
.portfolio-headline {
  margin-bottom: 1.5rem;
}

.portfolio-headline h1 {
  margin: 0;
  line-height: 1.05;
}

.headline-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.5rem 0.75rem;
  margin-top: 0.375rem;
}

.headline-asof {
  font-size: var(--text-sm);
  color: var(--color-ink-faint);
}
```

`h1` already picks up `--text-display` and `letter-spacing: -0.01em` from `ui/styles/base.css:54-57`. Leave `.chart-frame`, `.chart-veil` and `.change-percentage` untouched, and leave `layout: { padding: { top: 28 } }` in `portfolio-chart.vue` alone — it is chart breathing room, not overlay clearance.

- [ ] **Step 6: Run the tests**

```bash
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
npm test -- --run
```

Expected: PASS. `range-change-header.test.ts` mounts the component directly and is unaffected by the move.

- [ ] **Step 7: Eyeball the headline at each viewport**

```bash
npm run dev
```

Open `/` at 390px, 768px and 1440px. Confirm: the total does not wrap or clip at 390px (`--text-display` bottoms out at 2.5rem there); the meta row wraps cleanly under the number rather than overflowing; the chart no longer has a floating number over its top-left corner; the legend at the bottom of the chart is unchanged. Check the empty state by filtering to a platform with no summaries — the `v-if="latestSummary"` must hide the whole header.

- [ ] **Step 8: Commit**

```bash
git add ui/components/portfolio-summary.vue ui/components/portfolio-summary.test.ts
git commit -m "Lead the portfolio page with the total"
```

---

### Task 7: Re-record baselines and verify the whole set

All six changes are baseline-affecting; `--color-surface` alone touches every one of the 40 PNGs. Re-recording once at the end keeps the git history to a single snapshot commit instead of six.

Baselines recorded off live data decay within hours, so run the record and the confirming run back to back.

**Files:**

- Modify: `docs/superpowers/baseline/*.png` (40 files)

- [ ] **Step 1: Run the visual suite against the current baselines to see the damage**

```bash
npm run visual
```

Expected: FAIL on most of the 40 snapshots. Read the reported diff counts — a snapshot reporting **zero** diff pixels after all six changes is suspicious, since the surface change alone shifts every background. The pixel gate is blind to colour-only changes (`maxDiffPixels: 0` with `threshold` unset defaults to 0.2), so a green route is expected for changes that only moved colour, not geometry. Note which routes fall in which bucket before re-recording.

- [ ] **Step 2: Re-record**

```bash
npm run visual:update
```

- [ ] **Step 3: Confirm the recording is stable**

```bash
npm run visual
```

Expected: all green. A failure on the immediate second run means data drift, not a code defect — re-record and re-run rather than chasing it.

- [ ] **Step 4: Review the snapshot diff before committing**

```bash
git diff --stat docs/superpowers/baseline/
```

Open at least `route-summary.png` (or whichever baseline covers `/`), one instruments route and `modal-confirm.png` and confirm by eye: warm surface, flat button fills, red destructive confirm, the blue lightness ramp on the chart, right-aligned numeric columns with signed deltas, and the headline above the chart. This is the only place the whole change set is visible at once.

- [ ] **Step 5: Run the complete gate**

```bash
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
npm test -- --run
npm run visual
```

Expected: all three green.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/baseline
git commit -m "Re-record visual baselines"
```

- [ ] **Step 7: Push and open the PR**

`git push` and `gh pr create` fail under the Bash sandbox — SSH gets `publickey` denied and HTTPS gets a sandbox-injected HTTP 500. Run both with the sandbox disabled.

```bash
git push -u origin feature/visual-language-refinements
```

PR title: `Refine the visual language across the frontend`

PR body — Summary bullets covering the six changes, then a Test plan with checkboxes for `npm run lint-format`, `npm test -- --run`, `npm run visual`, and the manual viewport checks from Tasks 5 and 6. No AI attribution, no emoji.

- [ ] **Step 8: Watch CI**

```bash
gh pr checks --watch
```

If CI reds with zero failing tests, check the Actions artifact storage quota before the code — expired artifacts still count against it.

---

## Self-Review

**Spec coverage** — every approved item maps to a task:

| Approved change                                | Task                                             |
| ---------------------------------------------- | ------------------------------------------------ |
| 1. Money alignment + honest signs              | 4 (signs) and 5 (alignment)                      |
| 2. Headline `<h1>`, chart legend untouched     | 6, with the legend pinned as a Global Constraint |
| 3. One button system, destructive confirm red  | 3                                                |
| 5. Chart series lightness separation           | 2                                                |
| 6. `--color-surface` → `oklch(0.995 0.004 85)` | 1                                                |
| 8. `--text-label: 0.75rem` token               | 1                                                |
| 4. Brass on filter chips — **rejected**        | excluded, named in Global Constraints            |
| 7. Tablet overflow — **rejected**              | excluded, named in Global Constraints            |

**Type consistency** — `formatSignedCurrency(value: number, currency: string | undefined): string` and `formatSignedPercent(value: number): string` are defined in Task 4 Step 3 and used with those exact signatures in Steps 4, 6, 7 and in Task 4's tests. `PORTFOLIO_SERIES_COLORS` is defined in Task 2 Step 3 as a 4-element `string[]` and indexed `[0]`-`[3]` in Step 5. `ColumnDefinition` is consumed unchanged in Task 5. `latestSummary` is defined in Task 6 Step 3 and read in Step 4.

**Known open decisions for the reviewer:**

1. The series ramp is single-hue blue at hue 250, matching the swatches the user approved. That makes the primary screen's chart entirely blue in an app whose accent is brass (hue 74). It fixes the greyscale flatness completely, but a hue-varied ramp with the same lightness steps would fix it too while keeping brand hue in the chart. One constant to change if the rendered result reads wrong.
2. `formatCurrencyWithSign` keeps its misleading name — it strips signs. Renaming it to `formatMoney` would touch six files and change no pixels, so it is deliberately out of scope. Task 4 removes the two hand-rolled sign workarounds it caused.
3. `allocation-card.vue:42` gains two decimals (it was the only `.toFixed(0)` money site). If the compact card layout breaks at 390px, that single site can keep a zero-decimal variant.
