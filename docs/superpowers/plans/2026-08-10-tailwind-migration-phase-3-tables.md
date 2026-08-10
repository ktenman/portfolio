# Tailwind Migration Phase 3 (Tables) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the four table components to Tailwind utilities, delete `ui/utils/style-classes.ts`, and remove the app's only `v-html`.

**Architecture:** Mechanical class translation, one component per task, each verified by the Playwright pixel gate. Bootstrap _component_ classes (`.table`, `.btn`, `.badge`, `.alert`, `.form-control`) stay — only utilities migrate; Phase 9 drops the component layer. Two structural changes ride along because they are prerequisites, not improvements: `formatPriceChange` stops returning HTML, and `data-table`'s mobile-column filter stops sniffing class-name substrings.

**Tech Stack:** Vue 3.5 SFC, TypeScript 6, Tailwind v4 (`tw:` prefix, coexisting with Bootstrap 5.3), Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-08-07-bootstrap-to-tailwind-design.md`, Phase 3 row (line 138).

## Global Constraints

- Branch off `main` (phases 0–2 landed as `0f4e7a86`). Branch name `feature/1662-tailwind-phase-3-tables`. Never commit to `main`.
- Every Tailwind utility carries the `tw:` prefix. `ui/styles/theme.css:4` imports `tailwindcss/utilities.css` with `prefix(tw)` and **deliberately without** `layer(utilities)`. Do not add the layer — it silently reverts every migrated component.
- Exit criteria (spec line 138): **diff = 0**; 4 tests handled; no `v-html` remains.
- "diff = 0" = ImageMagick `compare -metric AE -fuzz 2%` against `docs/superpowers/baseline/`, masked regions excluded. Non-zero is a regression to fix, not accept — except the one sanctioned drift in Task 1, which is recorded in the PR.
- No code comments, ever. No AI attribution in commits or PRs. Commit subjects: uppercase imperative, ≤50 chars, no `feat:`/`fix:` prefix.
- `npm run lint-format` already exits non-zero from pre-existing knip findings. The gate is "no _new_ findings", not "exit 0".
- `eslint ui --fix` strips the `/* eslint-disable */` header from `ui/models/generated/domain-models.ts`. Revert that file rather than committing it.
- The `modal confirm` visual capture is destructive — its confirm button deletes all portfolio summary data. Never click confirm; keep the `route.abort()` on `**/api/portfolio-summary/recalculate**`.

## Class translation table

Breakpoints are already redefined to Bootstrap's values in `theme.css` (`sm 576px`, `md 768px`, `lg 992px`, `xl 1200px`, `2xl 1400px`), so responsive prefixes map 1:1.

| Bootstrap                                                                                                          | Tailwind                                               | Note                                                                                                                                                                                                                |
| ------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `d-none`                                                                                                           | `tw:hidden`                                            |                                                                                                                                                                                                                     |
| `d-none d-md-table-cell`                                                                                           | `tw:hidden! tw:md:table-cell!`                         | `!` required inside `.table` — Bootstrap's `.table > :not(caption) > * > *` outranks a bare utility. Precedent: `data-table.vue:85`.                                                                                |
| `d-none d-md-block`                                                                                                | `tw:hidden tw:md:block`                                | no `!` outside `.table`                                                                                                                                                                                             |
| `d-block d-md-none`                                                                                                | `tw:block tw:md:hidden`                                |                                                                                                                                                                                                                     |
| `d-none d-sm-inline`                                                                                               | `tw:hidden tw:sm:inline`                               |                                                                                                                                                                                                                     |
| `text-end`                                                                                                         | `tw:text-right`                                        |                                                                                                                                                                                                                     |
| `text-nowrap`                                                                                                      | `tw:whitespace-nowrap`                                 |                                                                                                                                                                                                                     |
| `fw-bold` / `fw-semibold` / `fw-medium`                                                                            | `tw:font-bold` / `tw:font-semibold` / `tw:font-medium` |                                                                                                                                                                                                                     |
| `text-muted`                                                                                                       | `tw:text-gray-600`                                     | **not** `tw:text-ink-muted`. `.text-muted` is `#6c757d` = `--color-gray-600`; `--color-ink-muted` is `#6b7280`. Using the wrong one is a guaranteed pixel diff.                                                     |
| `text-success` / `text-danger`                                                                                     | `tw:text-gain` / `tw:text-loss`                        | see Task 1 — `text-success` is a color change, `text-danger` is not                                                                                                                                                 |
| `m*-1` `m*-2` `m*-3` `m*-4` `m*-5`                                                                                 | `tw:m*-1` `tw:m*-2` `tw:m*-4` `tw:m*-6` `tw:m*-12`     | Bootstrap's spacer scale is `0/.25/.5/1/1.5/3rem`, Tailwind's is `0.25rem × n`. **`mb-3` is `tw:mb-4`, not `tw:mb-3`.** Same for `p*`, `g*`, `gap-*`. Mis-mapping this is the most likely cause of a non-zero diff. |
| `w-100` / `h-100`                                                                                                  | `tw:w-full` / `tw:h-full`                              |                                                                                                                                                                                                                     |
| `align-items-center` / `justify-content-between`                                                                   | `tw:items-center` / `tw:justify-between`               |                                                                                                                                                                                                                     |
| `ms-auto` / `me-auto` / `mx-auto`                                                                                  | `tw:ml-auto` / `tw:mr-auto` / `tw:mx-auto`             |                                                                                                                                                                                                                     |
| `.table` `.btn*` `.badge` `.alert*` `.form-control` `.form-select` `.input-group` `.spinner-border` `.row` `.col*` | **keep unchanged**                                     | Bootstrap component/grid layer, removed in Phase 9. Precedent: `crud-layout.vue:12` keeps `btn btn-primary` beside `tw:hidden tw:md:block`.                                                                         |

Scoped-CSS class names (`mobile-card`, `profit-column`, `metric-value`, …) are hooks for each file's `<style scoped>` block. **Keep them.** Migrating scoped CSS is Phase 8.

## File structure

| File                                                     | Change                                                                                  | Task |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------- | ---- |
| `ui/utils/formatters.ts`                                 | `getProfitClass`/`getAmountClass` return tokens; `formatPriceChange` returns plain text | 1    |
| `ui/utils/style-classes.ts`                              | **delete** (157 lines, only consumer is `formatters.ts`)                                | 1    |
| `ui/utils/style-classes.test.ts`                         | **delete** with the module it tests, not rewritten                                      | 1    |
| `ui/utils/formatters.test.ts`                            | 9 Bootstrap-class assertions updated                                                    | 1    |
| `ui/components/instruments/instrument-table.vue:270-273` | `v-html` removed                                                                        | 1    |
| `ui/components/shared/data-table.vue:31,129-136`         | `hideOnMobile` field replaces the `d-none` substring filter                             | 2    |
| `ui/config/table-columns.ts`                             | 6 column class strings migrated + `hideOnMobile`                                        | 2    |
| `ui/components/portfolio-summary.vue:172-195`            | 2 column class strings migrated + `hideOnMobile`                                        | 2    |
| `ui/components/shared/data-table.test.ts`                | regression test for the filter                                                          | 2    |
| `ui/components/transactions/transaction-table.vue`       | 373 lines, 35 utility tokens                                                            | 3    |
| `ui/components/instruments/instrument-table.vue`         | 758 lines, 81 utility tokens                                                            | 4    |
| `ui/components/etf/etf-breakdown-table.vue`              | 616 lines, 75 utility tokens                                                            | 5    |
| `ui/components/diversification/allocation-table.vue`     | 988 lines, 150 utility tokens                                                           | 6    |

---

### Task 1: Cut `style-classes.ts` and the `v-html`

**Files:**

- Modify: `ui/utils/formatters.ts:194-201`, `:228-244`, `:1`
- Delete: `ui/utils/style-classes.ts`, `ui/utils/style-classes.test.ts`
- Modify: `ui/utils/formatters.test.ts:306-335`
- Modify: `ui/components/instruments/instrument-table.vue:269-273`

**Interfaces:**

- Consumes: nothing from earlier tasks.
- Produces: `getProfitClass(value: number | null | undefined): string` returning `'tw:text-gain'`, `'tw:text-loss'`, or `''`. `getAmountClass(type: string): string` returning `'tw:text-gain'` or `'tw:text-loss'`. `formatPriceChange(item: InstrumentDto): string` returning plain text (`'-'` or `'+€1.23 / 4.56%'`), **no markup**. Tasks 3 and 4 rely on all three.

**Sanctioned drift — read before starting.** `.text-danger` is `#dc3545` and `--color-loss` is `#dc3545`: identical, no diff. `.text-success` is `#28a745` (`_bootstrap-overrides.scss:17`) but `--color-gain` is `#21c55d`: **a real color change on every positive profit figure.** In mobile cards it is already neutral — `_modern-enhancements.scss:855` overrides `.value.text-success` to `var(--modern-success)` = `#21c55d` — so the diff appears only in desktop table cells and totals rows.

This is Phase 10's defect #2 (palette collapse) arriving one phase early, and it is unavoidable: the spec directs Phase 3 to take the token colors directly (line 44), and every later phase also demands diff = 0, so the change has no cheaper home. Record it in the PR as accepted drift with this rationale, list the affected screenshots, and note that Phase 10 defect #2 shrinks to the remaining `#22c55e`/`#dc2626` occurrences.

If the reviewer rejects the drift, the fallback is to have both functions return the literal strings `'text-success'`/`'text-danger'` instead of tokens — `style-classes.ts` still dies, diff stays 0, and the color question moves to Phase 9. Do not take this path without being told to.

- [ ] **Step 1: Update the failing assertions in `formatters.test.ts`**

Replace lines 306-335 with:

```ts
describe('getProfitClass', () => {
  it('returns the gain token for zero and positive values', () => {
    expect(getProfitClass(100)).toBe('tw:text-gain')
    expect(getProfitClass(0.01)).toBe('tw:text-gain')
    expect(getProfitClass(0)).toBe('tw:text-gain')
  })

  it('returns the loss token for negative values', () => {
    expect(getProfitClass(-100)).toBe('tw:text-loss')
    expect(getProfitClass(-0.01)).toBe('tw:text-loss')
  })

  it('returns an empty string for null and undefined', () => {
    expect(getProfitClass(null)).toBe('')
    expect(getProfitClass(undefined)).toBe('')
  })
})

describe('getAmountClass', () => {
  it('returns the gain token for BUY', () => {
    expect(getAmountClass('BUY')).toBe('tw:text-gain')
  })

  it('returns the loss token for anything else', () => {
    expect(getAmountClass('SELL')).toBe('tw:text-loss')
    expect(getAmountClass('OTHER')).toBe('tw:text-loss')
    expect(getAmountClass('')).toBe('tw:text-loss')
  })
})
```

- [ ] **Step 2: Add a test pinning `formatPriceChange` to plain text**

Append to `ui/utils/formatters.test.ts`:

```ts
describe('formatPriceChange', () => {
  const item = {
    priceChangeAmount: 1.23,
    priceChangePercent: 4.56,
    baseCurrency: 'EUR',
  } as InstrumentDto

  it('returns text without markup', () => {
    expect(formatPriceChange(item)).not.toContain('<')
  })

  it('formats the absolute amount and percent', () => {
    expect(formatPriceChange(item)).toBe('+€1.23 / 4.56%')
  })

  it('returns a dash when the change is unknown', () => {
    expect(formatPriceChange({ ...item, priceChangeAmount: null })).toBe('-')
  })
})
```

Add `formatPriceChange` and `type InstrumentDto` to the existing imports at the top of the file.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `npm test -- --run ui/utils/formatters.test.ts`
Expected: FAIL — `expected 'text-success' to be 'tw:text-gain'`, and `formatPriceChange` returning a `<span>`.

If the second assertion's expected string does not match your locale's currency output, run the test once, read the actual value, and correct the expectation to it. Do not change `formatCurrencyWithSign`.

- [ ] **Step 4: Rewrite the three functions**

In `ui/utils/formatters.ts`, delete the `import { styleClasses } from './style-classes'` on line 1, then replace lines 194-201:

```ts
export const getProfitClass = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return ''
  return value >= 0 ? 'tw:text-gain' : 'tw:text-loss'
}

export const getAmountClass = (type: string): string => {
  return type === 'BUY' ? 'tw:text-gain' : 'tw:text-loss'
}
```

and replace `formatPriceChange` (lines 228-244):

```ts
export const formatPriceChange = (item: InstrumentDto): string => {
  const amount = item.priceChangeAmount
  const percent = item.priceChangePercent

  if (amount === null || amount === undefined || percent === null || percent === undefined) {
    return '-'
  }

  const currency = item.baseCurrency || 'EUR'
  const formattedAmount = formatCurrencyWithSign(Math.abs(amount), currency)
  const formattedPercent = Math.abs(percent).toFixed(2)

  return `${formattedAmount} / ${formattedPercent}%`
}
```

The colour that used to be baked into the returned span now comes from `getProfitClass`, which the call site already imports.

- [ ] **Step 5: Delete the module and its test**

```bash
git rm ui/utils/style-classes.ts ui/utils/style-classes.test.ts
```

- [ ] **Step 6: Remove the `v-html`**

In `ui/components/instruments/instrument-table.vue`, replace lines 269-273:

```vue
<template #cell-priceChange="{ item }">
  <span
    :class="[getChangeClass(item.id, 'priceChangeAmount'), getProfitClass(item.priceChangeAmount)]"
  >
    {{ formatPriceChange(item) }}
  </span>
</template>
```

`getProfitClass` is already imported at line 340; `formatPriceChange` at line 345. No import changes needed.

- [ ] **Step 7: Verify no `v-html` and no `style-classes` references remain**

```bash
grep -rn "v-html\|style-classes\|styleClasses" ui/
```

Expected: no output.

- [ ] **Step 8: Run the unit suite and the gates**

Run: `npm test -- --run` then `npm run lint-format`
Expected: all tests pass; no _new_ knip findings versus the pre-existing baseline. Check `git status ui/models/generated/domain-models.ts` and revert it if eslint stripped its header.

- [ ] **Step 9: Commit**

```bash
git add -A ui/utils ui/components/instruments/instrument-table.vue
git commit -m "Replace style-classes registry with color tokens"
```

---

### Task 2: Replace the mobile-column filter's substring sniff

**Files:**

- Modify: `ui/components/shared/data-table.vue:31`, `:129-136`
- Modify: `ui/config/table-columns.ts:14-46`, `:56-60`
- Modify: `ui/components/portfolio-summary.vue:172-195`
- Modify: `ui/components/shared/data-table.test.ts`

**Interfaces:**

- Consumes: nothing from Task 1.
- Produces: `ColumnDefinition` gains `hideOnMobile?: boolean`. Tasks 3–6 do not touch column definitions, but any new column added later must set it explicitly rather than encoding visibility in `class`.

**Why this is in scope.** `data-table.vue:31` currently picks mobile-card columns with `columns.filter(col => !col.class?.includes('d-none'))`. The moment Task 2 rewrites `class: 'd-none d-md-table-cell'` to `class: 'tw:hidden! tw:md:table-cell!'`, that substring stops matching and **every hidden column reappears in every mobile card** — a large, silent visual regression. Swapping the class strings and the filter in one commit is mandatory, not optional polish.

The same line carries a live latent bug worth killing while we are here: `'d-md-none'.includes('d-none')` is `true`, so a column meant to be _mobile-only_ would be dropped from mobile cards — exactly backwards. An explicit boolean removes both problems and is a smaller diff than any string-matching replacement.

`portfolio-summary.vue` belongs to Phase 7, but it is the third feeder of this same filter. Touching its two column entries is required for correctness now; its markup is left alone.

- [ ] **Step 1: Write the failing regression test**

Append to `ui/components/shared/data-table.test.ts`:

```ts
describe('mobile card column selection', () => {
  it('omits columns marked hideOnMobile', () => {
    const wrapper = mount(DataTable, {
      props: {
        items: [{ id: 1, shown: 'yes', hidden: 'no' }],
        columns: [
          { key: 'shown', label: 'Shown' },
          { key: 'hidden', label: 'Hidden', hideOnMobile: true },
        ],
      },
    })
    const labels = wrapper.findAll('.mobile-card-item .label').map(node => node.text())
    expect(labels).toEqual(['Shown'])
  })

  it('keeps columns whose class merely mentions a hidden breakpoint', () => {
    const wrapper = mount(DataTable, {
      props: {
        items: [{ id: 1, mobileOnly: 'yes' }],
        columns: [{ key: 'mobileOnly', label: 'Mobile Only', class: 'tw:md:hidden' }],
      },
    })
    const labels = wrapper.findAll('.mobile-card-item .label').map(node => node.text())
    expect(labels).toEqual(['Mobile Only'])
  })
})
```

Match the existing file's mount helper and import style rather than copying these mounts verbatim if it already has one.

- [ ] **Step 2: Run it to verify it fails**

Run: `npm test -- --run ui/components/shared/data-table.test.ts`
Expected: FAIL — the first test shows both labels, because `hideOnMobile` is not yet read.

- [ ] **Step 3: Add the field and use it**

In `ui/components/shared/data-table.vue`, extend the interface at line 129:

```ts
export interface ColumnDefinition {
  key: string
  label: string
  formatter?: (value: any, item?: any) => string
  class?: string
  hideOnMobile?: boolean
  sortable?: boolean
  sortKey?: string
}
```

and replace line 31:

```vue
v-for="column in columns.filter(col => !col.hideOnMobile)"
```

- [ ] **Step 4: Migrate the three feeders**

In `ui/config/table-columns.ts`, every entry whose `class` starts with `d-none` gains `hideOnMobile: true` and has its class translated:

```ts
  {
    key: 'totalInvestment',
    label: 'Invested',
    formatter: formatCurrency,
    class: 'tw:hidden! tw:md:table-cell!',
    hideOnMobile: true,
  },
```

```ts
  {
    key: 'priceChange',
    label: '24H',
    class: 'tw:hidden! tw:lg:table-cell! price-change-column',
    hideOnMobile: true,
    sortKey: 'priceChangeAmount',
  },
```

```ts
  {
    key: 'xirrAnnualReturn',
    label: 'Annual',
    formatter: formatPercentageFromDecimal,
    class: 'tw:hidden! tw:xl:table-cell!',
    hideOnMobile: true,
  },
```

```ts
  {
    key: 'portfolioWeight',
    label: 'Weight',
    class: 'tw:hidden! tw:xl:table-cell! weight-column',
    hideOnMobile: true,
    sortKey: 'currentValue',
  },
```

```ts
  { key: 'ter', label: 'TER', class: 'tw:hidden! tw:xl:table-cell!', hideOnMobile: true },
```

```ts
  {
    key: 'averageCost',
    label: 'Average Cost',
    formatter: formatCurrency,
    class: 'tw:hidden! tw:sm:table-cell!',
    hideOnMobile: true,
  },
```

In `ui/components/portfolio-summary.vue`, both `earningsPerDay` and `unrealizedProfit`:

```ts
  {
    key: 'earningsPerDay',
    label: 'Earnings Per Day',
    formatter: formatCurrencyWithSymbol,
    class: 'tw:hidden! tw:md:table-cell!',
    hideOnMobile: true,
  },
```

```ts
  {
    key: 'unrealizedProfit',
    label: 'Unrealized Profit',
    formatter: formatCurrencyWithSymbol,
    class: 'tw:hidden! tw:md:table-cell!',
    hideOnMobile: true,
  },
```

- [ ] **Step 5: Verify no feeder still encodes visibility in a class string**

```bash
grep -rn "d-none" ui/config/ ui/components/shared/ ui/components/portfolio-summary.vue
```

Expected: no output.

- [ ] **Step 6: Run the tests**

Run: `npm test -- --run` then `npm run lint-format`
Expected: PASS, including both new cases.

- [ ] **Step 7: Capture and diff the three affected routes**

```bash
npx playwright test ui/tests/visual/routes.spec.ts -g "summary|instruments|transactions"
```

Expected: 9 passed (3 routes × 3 projects). Any failure here means a column changed visibility — read the diff image before touching anything else.

- [ ] **Step 8: Commit**

```bash
git add ui/components/shared ui/config/table-columns.ts ui/components/portfolio-summary.vue
git commit -m "Make mobile column visibility explicit"
```

---

### Task 3: Migrate `transaction-table.vue`

**Files:**

- Modify: `ui/components/transactions/transaction-table.vue` (373 lines, ~35 utility tokens)
- Test: `ui/components/transactions/transaction-table.test.ts` (0 Bootstrap assertions — expected to pass untouched)

**Interfaces:**

- Consumes: `getProfitClass`, `getAmountClass` from Task 1, already returning `tw:text-gain`/`tw:text-loss` — the `:class` bindings at lines 61, 78, 133, 152 need no edit, only the surrounding literal classes do.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Translate every utility class in the template**

Walk the `<template>` block top to bottom and apply the translation table. Do not touch: the `<script setup>` block, the `<style scoped>` block, Bootstrap component classes, or scoped-CSS hook names such as `amount-value`.

List what you are about to change first:

```bash
grep -nE "class=\"[^\"]*\b(d-|text-end|text-muted|text-nowrap|fw-|m[btlrxy]?-[0-9]|p[btlrxy]?-[0-9]|gap-[0-9]|w-100|align-items|justify-content|ms-auto|me-auto)" ui/components/transactions/transaction-table.vue
```

- [ ] **Step 2: Verify nothing was missed**

```bash
grep -nE "\b(d-none|d-block|d-flex|d-md-|d-lg-|d-xl-|d-sm-|text-end|text-muted|text-nowrap|fw-bold|fw-semibold|fw-medium)\b" ui/components/transactions/transaction-table.vue
```

Expected: no output. Occurrences inside `<style scoped>` are Phase 8's problem — if the only hits are there, that is correct; confirm by line number.

- [ ] **Step 3: Run the unit tests**

Run: `npm test -- --run ui/components/transactions/transaction-table.test.ts`
Expected: PASS unchanged. This file asserts on behaviour, not classes; if it fails, you changed structure rather than classes — revert and redo.

- [ ] **Step 4: Diff the transactions route**

```bash
npx playwright test ui/tests/visual/routes.spec.ts -g transactions
```

Expected: 3 passed. On failure, open the diff PNG under `test-results/` and fix the class that moved a pixel — most often a `m*-3` → `tw:m*-3` slip that should be `tw:m*-4`.

- [ ] **Step 5: Commit**

```bash
git add ui/components/transactions/transaction-table.vue
git commit -m "Migrate transaction table to Tailwind"
```

---

### Task 4: Migrate `instrument-table.vue`

**Files:**

- Modify: `ui/components/instruments/instrument-table.vue` (758 lines, ~81 utility tokens)
- Modify: `ui/components/instruments/instrument-table.test.ts` (26 Bootstrap-ish assertions)

**Interfaces:**

- Consumes: `getProfitClass`, `formatPriceChange` from Task 1. The `v-html` is already gone — if you still see one at line ~272, Task 1 was not applied; stop and check.
- Produces: nothing later tasks depend on.

This is the densest file: a totals footer row at lines 16-81 carrying seven `d-none d-*-table-cell` cells, plus mobile metric rows at 120-134.

- [ ] **Step 1: Translate every utility class in the template**

Apply the translation table. The footer cells at lines 16, 27, 45, 65, 80, 81 are inside `<table>`, so their responsive utilities need the `!` suffix: `fw-bold text-nowrap d-none d-md-table-cell` becomes `tw:font-bold tw:whitespace-nowrap tw:hidden! tw:md:table-cell!`.

- [ ] **Step 2: Verify nothing was missed**

```bash
grep -nE "\b(d-none|d-block|d-flex|d-md-|d-lg-|d-xl-|d-sm-|text-end|text-muted|text-nowrap|fw-bold|fw-semibold|fw-medium)\b" ui/components/instruments/instrument-table.vue
```

Expected: hits only inside `<style scoped>`, if any.

- [ ] **Step 3: Run the unit tests and read every failure**

Run: `npm test -- --run ui/components/instruments/instrument-table.test.ts`

Failures fall into two kinds, handled differently:

- An assertion on a class string (`expect(...classes()).toContain('text-success')`) — update the expectation to the new token.
- An assertion on `v-html`-produced markup, or on `.html()` containing a `<span>` from `formatPriceChange` — rewrite it to assert on rendered text plus the class binding, since the markup no longer exists.

Do not delete a failing assertion to make the suite green.

- [ ] **Step 4: Verify the suite passes**

Run: `npm test -- --run ui/components/instruments/instrument-table.test.ts`
Expected: PASS.

- [ ] **Step 5: Diff the instruments route and its three modals**

```bash
npx playwright test ui/tests/visual/routes.spec.ts -g instruments
npx playwright test ui/tests/visual/states.spec.ts -g "instrument|xirr-windows|annual-windows"
```

Expected: 3 passed for the route; 6 passed for the modals (mobile + desktop each, tablet skipped).

Positive profit figures in the desktop table are expected to differ here — that is Task 1's recorded green drift. Confirm the diff is confined to green text by inspecting the diff PNG; if a _layout_ pixel moved, it is a real regression.

- [ ] **Step 6: Commit**

```bash
git add ui/components/instruments/instrument-table.vue ui/components/instruments/instrument-table.test.ts
git commit -m "Migrate instrument table to Tailwind"
```

---

### Task 5: Migrate `etf-breakdown-table.vue`

**Files:**

- Modify: `ui/components/etf/etf-breakdown-table.vue` (616 lines, ~75 utility tokens)
- Modify: `ui/components/etf/etf-breakdown-table.test.ts` (4 Bootstrap-ish assertions)

**Interfaces:**

- Consumes: nothing from Tasks 1–4.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Translate every utility class in the template**

Apply the translation table. Keep `.company-logo.clickable` intact — the `modal logo-replacement` capture selects on it.

- [ ] **Step 2: Verify nothing was missed**

```bash
grep -nE "\b(d-none|d-block|d-flex|d-md-|d-lg-|d-xl-|d-sm-|text-end|text-muted|text-nowrap|fw-bold|fw-semibold|fw-medium)\b" ui/components/etf/etf-breakdown-table.vue
```

Expected: hits only inside `<style scoped>`, if any.

- [ ] **Step 3: Run the unit tests, updating the 4 class assertions**

Run: `npm test -- --run ui/components/etf/etf-breakdown-table.test.ts`
Expected: PASS once the four class expectations name the new utilities.

- [ ] **Step 4: Diff the route, the logo modal, and the spinner state**

```bash
npx playwright test ui/tests/visual/routes.spec.ts -g etf-breakdown
npx playwright test ui/tests/visual/states.spec.ts -g "logo-replacement|state spinner"
```

Expected: 3 passed for the route; 3 passed for the modal and spinner.

- [ ] **Step 5: Commit**

```bash
git add ui/components/etf
git commit -m "Migrate ETF breakdown table to Tailwind"
```

---

### Task 6: Migrate `allocation-table.vue`

**Files:**

- Modify: `ui/components/diversification/allocation-table.vue` (988 lines, ~150 utility tokens)
- Modify: `ui/components/diversification/allocation-table.test.ts` (4 Bootstrap-ish assertions)

**Interfaces:**

- Consumes: nothing from Tasks 1–5.
- Produces: nothing.

This file runs its **own** mobile/desktop split, independent of `data-table`'s: `d-block d-md-none` at line 90 and `d-none d-md-block` at line 128. Task 2's `hideOnMobile` does not apply here — these are plain markup and translate directly to `tw:block tw:md:hidden` and `tw:hidden tw:md:block`. The six `d-none d-sm-inline` button labels at lines 340-389 become `tw:hidden tw:sm:inline`; the buttons carry `aria-label="Export"` and `aria-label="Import"` that the `config-export` / `config-import` captures click on — do not rename them.

- [ ] **Step 1: Translate every utility class in the template**

Apply the translation table. At 988 lines this is the largest file in the phase; work in template order and do not reflow markup.

- [ ] **Step 2: Verify nothing was missed**

```bash
grep -nE "\b(d-none|d-block|d-flex|d-md-|d-lg-|d-xl-|d-sm-|text-end|text-muted|text-nowrap|fw-bold|fw-semibold|fw-medium)\b" ui/components/diversification/allocation-table.vue
```

Expected: hits only inside `<style scoped>`, if any.

- [ ] **Step 3: Run the unit tests**

Run: `npm test -- --run ui/components/diversification/allocation-table.test.ts`
Expected: PASS once the four class expectations are updated.

- [ ] **Step 4: Diff the route and both config modals**

```bash
npx playwright test ui/tests/visual/routes.spec.ts -g diversification
npx playwright test ui/tests/visual/states.spec.ts -g "config-export|config-import"
```

Expected: 3 passed for the route; 4 passed for the modals.

- [ ] **Step 5: Commit**

```bash
git add ui/components/diversification/allocation-table.vue ui/components/diversification/allocation-table.test.ts
git commit -m "Migrate allocation table to Tailwind"
```

---

### Task 7: Verify the phase and open the PR

**Files:** none modified unless a check fails.

**Interfaces:**

- Consumes: every preceding task.
- Produces: the PR.

- [ ] **Step 1: Confirm the exit criteria mechanically**

```bash
grep -rn "v-html" ui/ ; \
grep -rn "style-classes\|styleClasses" ui/ ; \
ls ui/utils/style-classes.ts 2>&1
```

Expected: no output from the first two; "No such file or directory" from the third.

- [ ] **Step 2: Run the full visual suite**

```bash
npx playwright test
```

Expected: **41 passed, 25 skipped**, roughly 2.8 minutes. Then confirm no baseline PNG was silently re-recorded:

```bash
git status docs/superpowers/baseline/
```

Expected: clean. A modified PNG means a screenshot was accepted instead of diffed — revert it and investigate.

- [ ] **Step 3: Run `impeccable audit` on the changed files**

```bash
impeccable audit $(git diff --name-only main...HEAD | grep -E '\.(vue|ts)$')
```

Fix what it flags in the file that owns it; do not fix adjacent code.

- [ ] **Step 4: Run both gates one final time**

```bash
npm run lint-format
npm test -- --run
```

Expected: all unit tests pass; no _new_ knip findings. `git status ui/models/generated/domain-models.ts` clean, or reverted.

- [ ] **Step 5: Open the PR**

Requires `dangerouslyDisableSandbox: true` — `git push` and `gh pr create` fail under the Bash sandbox.

```bash
git push -u origin feature/1662-tailwind-phase-3-tables
```

PR body: Summary bullets covering the four tables, the `style-classes.ts` deletion, the `v-html` removal, and the `hideOnMobile` filter change; a Test plan with the visual-suite result (41/25) and unit-suite result; the **accepted drift** section from Task 1 naming the affected screenshots and the rationale; and `Closes #1662` only if that issue tracks this phase alone — otherwise reference it without closing.

No AI attribution anywhere in the commits or the PR body.

---

## Deviations from the spec, and why

Three, all discovered by reading the code rather than the spec:

1. **`formatters.test.ts` is a fifth coupled test file.** The spec's Phase-3 list (line 123) names `allocation-table`, `etf-breakdown-table`, `instrument-table`, `style-classes`. But `formatters.test.ts` asserts `getProfitClass(100) === 'text-success'` nine times and breaks the moment the tokens change. Handled in Task 1. `transaction-table.test.ts` is correctly absent — it has zero class assertions.

2. **`table-columns.ts`, `data-table.vue:31`, and `portfolio-summary.vue`'s columns are in scope.** No phase claims them, but they encode Bootstrap visibility classes that this phase's tables consume through a substring match. Leaving them would put every hidden column back into every mobile card. Handled in Task 2.

3. **`.text-success` is a color change, `.text-danger` is not.** `$success` is `#28a745` and `--color-gain` is `#21c55d`, so "diff = 0" and "take the token colors directly" cannot both hold. Task 1 documents the drift, its blast radius, and the fallback if a reviewer rejects it.
