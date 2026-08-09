# Tailwind Migration Phases 0–2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a reproducible visual-regression harness, capture the pre-migration baseline, then migrate the five shared primitive components and extract the duplicated platform filter — all with a provably zero-pixel diff.

**Architecture:** A Playwright screenshot suite captures every route and interactive state at three viewports and stores the images in git. Each subsequent component migration re-runs that suite; a non-zero diff fails the run. Components move from Bootstrap class names to `tw:`-prefixed Tailwind utilities, with the target values read out of the live browser's computed styles rather than guessed from Bootstrap's source.

**Tech Stack:** Vue 3.5, TypeScript, Vite 8, Tailwind v4.3.3 (`@tailwindcss/vite`, `prefix(tw)`), Bootstrap 5.3 (being removed), Playwright (new, dev-only), Vitest.

## Scope

This plan covers Phases 0, 1, and 2 of the 11-phase migration in `docs/superpowers/specs/2026-08-07-bootstrap-to-tailwind-design.md`. Phases 3–10 get their own plans.

The split is deliberate. Phase 0 produces the measuring instrument, and Phase 1 is the first real use of it. Until a component has actually been migrated and diffed against a committed baseline, every step written for Phases 3–10 is a guess about how much hand-tuning a translation needs. Writing those tasks now would mean writing them twice.

## Global Constraints

- Branch: `feature/1662-tailwind-v4-migration`. Never commit to `main`.
- Every Tailwind utility carries the `tw:` prefix until Phase 9. Unprefixed Bootstrap-colliding names silently break layout.
- **NEVER add code comments.** No `//`, no `/* */`, no `/** */`. This is a hard project rule.
- Commit subjects: uppercase imperative, ≤50 chars, no `feat:`/`fix:`/`chore:` prefixes.
- No AI attribution in commits or PRs.
- File naming: kebab-case, no `-new`/`-improved`/`-refactored` suffixes.
- Vue components stay under 200 lines.
- After any UI change run `npm run lint-format` and `npm test -- --run`.
- `npm run lint-format` already exits non-zero from pre-existing knip findings. The gate is "no new findings", not "exit 0".
- `eslint ui --fix` corrupts `ui/models/generated/domain-models.ts` by stripping its `/* eslint-disable */` header. After every `lint-format`, run `git checkout -- ui/models/generated/domain-models.ts`. Never commit that file.
- Do not edit `ui/models/generated/domain-models.ts` by hand; it is generated from Kotlin DTOs.

## File Structure

**Created:**

| File                                       | Responsibility                                                                                              |
| ------------------------------------------ | ----------------------------------------------------------------------------------------------------------- |
| `playwright.config.ts`                     | Playwright config: baseURL, three viewport projects, snapshot path pointing at the committed baseline       |
| `ui/tests/visual/routes.spec.ts`           | Captures the 6 routes at 3 viewports                                                                        |
| `ui/tests/visual/states.spec.ts`           | Captures modals, dropdown, toasts, loading and empty states                                                 |
| `ui/tests/visual/volatile.ts`              | The list of selectors masked because live market data changes between runs                                  |
| `scripts/computed-style.mjs`               | Dumps an element's computed styles from the running app, so migrations copy real values instead of guessing |
| `docs/superpowers/baseline/**`             | The committed reference PNGs                                                                                |
| `ui/components/shared/platform-filter.vue` | The platform filter markup, extracted from 4 views                                                          |

**Modified:**

| File                                               | Change                                                                      |
| -------------------------------------------------- | --------------------------------------------------------------------------- |
| `package.json`                                     | Add `@playwright/test` devDependency and `visual` / `visual:update` scripts |
| `knip.json`                                        | Ignore the Playwright entry points                                          |
| `ui/components/shared/loading-spinner.vue`         | Bootstrap → Tailwind                                                        |
| `ui/components/shared/form-input.vue`              | Bootstrap → Tailwind                                                        |
| `ui/components/shared/crud-layout.vue`             | Bootstrap → Tailwind                                                        |
| `ui/components/shared/skeleton-loader.vue`         | Bootstrap → Tailwind                                                        |
| `ui/components/shared/data-table.vue`              | Bootstrap → Tailwind                                                        |
| `ui/components/shared/data-table.test.ts`          | Assert on new markup                                                        |
| `ui/components/portfolio-summary.vue`              | Use `<PlatformFilter>`                                                      |
| `ui/components/transactions/transactions-view.vue` | Use `<PlatformFilter>`, plus `:deep()` on its mobile button rule            |
| `ui/components/instruments/instruments-view.vue`   | Use `<PlatformFilter>`                                                      |
| `ui/components/etf/etf-breakdown.vue`              | Use `<PlatformFilter>`, plus `:deep()` on its active-button override        |

## Deviation from the spec: Playwright instead of ImageMagick

The spec specifies capture via Chrome DevTools and diffing via `compare -metric AE -fuzz 2%`. **This plan uses Playwright's built-in `toHaveScreenshot()` instead.** The reason is arithmetic: 40 captures across 11 phases is 440 captures, and a DevTools-driven capture is an agent tool call, not a command. It cannot be re-run by a person, by CI, or by a subagent, which defeats the spec's own requirement that any phase reproduce the baseline exactly.

Playwright provides baseline storage, per-pixel comparison with a configurable threshold, region masking, and animation freezing as first-class features — the exact set the spec was about to hand-roll. It replaces a capture script, ImageMagick, and a prose index with one config file. The cost is one dev-only dependency.

The spec's `maxDiffPixels: 0` intent is preserved exactly.

---

### Task 1: Playwright harness

**Files:**

- Create: `playwright.config.ts`
- Create: `ui/tests/visual/volatile.ts`
- Create: `ui/tests/visual/routes.spec.ts`
- Modify: `package.json`
- Modify: `knip.json`

**Interfaces:**

- Consumes: nothing.
- Produces: `npm run visual` (verify against baseline, fails on any diff), `npm run visual:update` (rewrite baseline). `VOLATILE_SELECTORS: string[]` exported from `ui/tests/visual/volatile.ts`.

- [ ] **Step 1: Install Playwright**

```bash
npm install -D @playwright/test
npx playwright install chromium
```

- [ ] **Step 2: Write the config**

Create `playwright.config.ts`:

```ts
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './ui/tests/visual',
  snapshotPathTemplate: 'docs/superpowers/baseline/{arg}-{projectName}{ext}',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:61234',
    screenshot: 'off',
  },
  expect: {
    toHaveScreenshot: {
      maxDiffPixels: 0,
      animations: 'disabled',
      caret: 'hide',
      scale: 'css',
    },
  },
  projects: [
    {
      name: 'mobile',
      use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 } },
    },
    {
      name: 'tablet',
      use: { ...devices['Desktop Chrome'], viewport: { width: 768, height: 1024 } },
    },
    {
      name: 'desktop',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
})
```

- [ ] **Step 3: Add the npm scripts**

In `package.json` `scripts`, add:

```json
"visual": "playwright test",
"visual:update": "playwright test --update-snapshots"
```

- [ ] **Step 4: Declare the volatile selector list**

Create `ui/tests/visual/volatile.ts`:

```ts
export const VOLATILE_SELECTORS: string[] = []
```

It starts empty on purpose. Step 8 fills it from evidence rather than guesswork.

- [ ] **Step 5: Write the route capture spec**

Create `ui/tests/visual/routes.spec.ts`:

```ts
import { test, expect } from '@playwright/test'
import { VOLATILE_SELECTORS } from './volatile'

const ROUTES = [
  { path: '/', name: 'summary' },
  { path: '/transactions', name: 'transactions' },
  { path: '/instruments', name: 'instruments' },
  { path: '/etf-breakdown', name: 'etf-breakdown' },
  { path: '/diversification', name: 'diversification' },
  { path: '/calculator', name: 'calculator' },
]

for (const route of ROUTES) {
  test(`route ${route.name}`, async ({ page }) => {
    await page.goto(route.path)
    await page.waitForLoadState('networkidle')
    await expect(page).toHaveScreenshot(`route-${route.name}.png`, {
      fullPage: true,
      mask: VOLATILE_SELECTORS.map(s => page.locator(s)),
    })
  })
}
```

- [ ] **Step 6: Start the app**

```bash
npm run docker:up
npm run test:wait
```

If `test:wait` reports a timeout, start the services with `npm run test:setup` instead and re-run `npm run test:wait`.

- [ ] **Step 7: Generate the first baseline**

Run: `npm run visual:update`
Expected: 18 snapshots written under `docs/superpowers/baseline/`.

- [ ] **Step 8: Discover the volatile regions**

Run: `npm run visual`
Expected: some route tests FAIL, because live prices and chart canvases changed since Step 7.

For each failure, open `test-results/<test-name>/<snapshot>-diff.png` and identify what moved. Add a stable selector for each moving region to `VOLATILE_SELECTORS` in `ui/tests/visual/volatile.ts`. Prefer an existing class already in the markup; if none exists, add a `data-testid` to the component rather than inventing a brittle structural selector.

Repeat until `npm run visual` passes twice in a row with no code changes between runs. That two-clean-runs condition is the real exit criterion for this step — a single pass can be luck.

- [ ] **Step 9: Re-baseline with masks applied**

Run: `npm run visual:update && npm run visual`
Expected: PASS.

- [ ] **Step 10: Keep knip quiet**

In `knip.json`, add the visual tests to `entry`:

```json
"entry": ["ui/main.ts", "ui/index.html", "ui/tests/visual/*.spec.ts", "playwright.config.ts"]
```

Run: `npm run check-unused`
Expected: no new findings about `@playwright/test` or the visual specs.

- [ ] **Step 11: Commit**

```bash
git add playwright.config.ts ui/tests/visual package.json package-lock.json knip.json docs/superpowers/baseline
git commit -m "Add visual regression harness"
```

---

### Task 2: Capture the interactive states

**Files:**

- Create: `ui/tests/visual/states.spec.ts`
- Modify: `docs/superpowers/baseline/**`

**Interfaces:**

- Consumes: `VOLATILE_SELECTORS` from Task 1.
- Produces: baseline snapshots named `modal-*.png`, `dropdown-quick-dates.png`, `toast-*.png`, `state-*.png`.

**The spec undercounts the modals.** It lists 6; there are 7, because `diversification-calculator.vue:98` and `:107` mount two separate `<ConfigDialog>` instances — `exportConfigDialog` and `importConfigDialog`. The capture total is therefore **40**, not 38: 18 routes + 14 modals + 1 dropdown + 4 toasts + 3 states. Amend the spec's count in the same commit as the baseline.

> **Do not click through the confirm dialog.** The confirm dialog on `/` is opened by `handleRecalculate` (`portfolio-summary.vue:225`), whose confirm button **deletes all portfolio summary data and recalculates from scratch**. The capture below opens it and screenshots it. It must never click the confirm button. The test presses Escape to dismiss.

- [ ] **Step 1: Write the states spec**

Create `ui/tests/visual/states.spec.ts`. Every selector below is verified against the current source:

```ts
import { test, expect } from '@playwright/test'
import { VOLATILE_SELECTORS } from './volatile'

const MODALS = [
  { name: 'instrument', route: '/instruments', trigger: '#addNewItem' },
  { name: 'xirr-windows', route: '/instruments', trigger: '[title^="Show XIRR over"]' },
  { name: 'annual-windows', route: '/instruments', trigger: '[title^="Show buy-and-hold"]' },
  { name: 'logo-replacement', route: '/etf-breakdown', trigger: '.company-logo.clickable' },
]

for (const modal of MODALS) {
  test(`modal ${modal.name}`, async ({ page }) => {
    await page.goto(modal.route)
    await page.waitForLoadState('networkidle')
    await page.click(modal.trigger)
    await expect(page.locator('.modal.show')).toBeVisible()
    await expect(page).toHaveScreenshot(`modal-${modal.name}.png`, {
      mask: VOLATILE_SELECTORS.map(s => page.locator(s)),
    })
  })
}
```

The `[title^=...]` selectors target the buttons at `instrument-table.vue:56` and `:71`, which share the class `.xirr-trigger` and are distinguishable only by their `title`. At the 390px viewport those cells are `d-none d-xl-table-cell` and the mobile buttons at `:213`/`:224` render instead — they carry `.xirr-trigger-mobile` and no `title`. If the mobile modal capture fails to find the trigger, add a `data-testid` to both the desktop and mobile buttons rather than branching the test on viewport.

- [ ] **Step 2: Add the two config dialogs and the confirm dialog**

Append to `ui/tests/visual/states.spec.ts`:

```ts
const CONFIG_DIALOGS = [
  { name: 'config-export', trigger: 'button:has-text("Export")' },
  { name: 'config-import', trigger: 'button:has-text("Import")' },
]

for (const dialog of CONFIG_DIALOGS) {
  test(`modal ${dialog.name}`, async ({ page }) => {
    await page.goto('/diversification')
    await page.waitForLoadState('networkidle')
    await page.click(dialog.trigger)
    await expect(page.locator('.modal.show')).toBeVisible()
    await expect(page).toHaveScreenshot(`modal-${dialog.name}.png`)
  })
}

test('modal confirm', async ({ page }) => {
  await page.goto('/')
  await page.waitForLoadState('networkidle')
  await page.click('button:has-text("Recalculate")')
  await expect(page.locator('.modal.show')).toBeVisible()
  await expect(page).toHaveScreenshot('modal-confirm.png', {
    mask: VOLATILE_SELECTORS.map(s => page.locator(s)),
  })
  await page.keyboard.press('Escape')
  await expect(page.locator('.modal.show')).toHaveCount(0)
})
```

Verify the Export/Import and Recalculate button text against the rendered page before running; if a label differs, use the actual label. The trailing Escape in the confirm test is not optional — it guarantees the run leaves no dialog armed.

test('dropdown quick dates', async ({ page }) => {
await page.goto('/transactions')
await page.waitForLoadState('networkidle')
await page.click('[data-bs-toggle="dropdown"]')
await expect(page.locator('.dropdown-menu.show')).toBeVisible()
await expect(page).toHaveScreenshot('dropdown-quick-dates.png', {
mask: VOLATILE_SELECTORS.map(s => page.locator(s)),
})
})

const TOASTS = ['success', 'error', 'info', 'warning'] as const

for (const variant of TOASTS) {
test(`toast ${variant}`, async ({ page }) => {
await page.goto('/')
await page.waitForLoadState('networkidle')
await page.evaluate(async v => {
const mod = await import('/composables/use-toast.ts')
mod.useToast()[v](`Baseline ${v} message`)
}, variant)
await expect(page.locator('.toast')).toBeVisible()
await expect(page).toHaveScreenshot(`toast-${variant}.png`)
})
}

````

- [ ] **Step 3: Verify the toast import path resolves**

Run: `npx playwright test -g "toast success" --project=desktop --update-snapshots`
Expected: PASS with one snapshot written.

If the dynamic import fails because Vite serves the module under a different path, replace the `page.evaluate` body with a click on a real UI action that produces a toast of that type, and record which action you used in the test name. Do not stub the toast — the point is to capture the real rendered element.

- [ ] **Step 4: Capture the loading and empty states**

Append to `ui/tests/visual/states.spec.ts`:

```ts
test('state loading skeleton', async ({ page }) => {
  await page.route('**/api/**', route => setTimeout(() => route.continue(), 5000))
  await page.goto('/')
  await expect(page.locator('.skeleton-loader, [class*="skeleton"]').first()).toBeVisible()
  await expect(page).toHaveScreenshot('state-loading.png')
})

test('state empty table', async ({ page }) => {
  await page.route('**/api/transactions**', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  )
  await page.goto('/transactions')
  await page.waitForLoadState('networkidle')
  await expect(page).toHaveScreenshot('state-empty.png')
})

test('state error', async ({ page }) => {
  await page.route('**/api/transactions**', route => route.fulfill({ status: 500, body: '' }))
  await page.goto('/transactions')
  await page.waitForLoadState('networkidle')
  await expect(page).toHaveScreenshot('state-error.png')
})
````

If the skeleton selector does not match, read `ui/components/shared/skeleton-loader.vue` for its real root class and use that.

- [ ] **Step 5: Restrict the single-viewport captures**

Modals capture at mobile and desktop; the dropdown, toasts and states capture at desktop only. Add to the top of each desktop-only test:

```ts
test.skip(({}, testInfo) => testInfo.project.name !== 'desktop')
```

and to each modal test:

```ts
test.skip(({}, testInfo) => testInfo.project.name === 'tablet')
```

- [ ] **Step 6: Generate the full baseline**

Run: `npm run visual:update`
Expected: 40 snapshots total across `docs/superpowers/baseline/`.

Verify the count:

```bash
find docs/superpowers/baseline -name '*.png' | wc -l
```

Expected: 40

- [ ] **Step 7: Prove it is reproducible**

Run: `npm run visual`
Expected: PASS, all 40.

Run it a second time. Expected: PASS again. Two consecutive clean runs is the exit criterion.

- [ ] **Step 8: Commit**

```bash
git add ui/tests/visual/states.spec.ts docs/superpowers/baseline
git commit -m "Capture pre-migration visual baseline"
```

- [ ] **Step 9: Open the Phase 0 PR**

```bash
git push -u origin feature/1662-tailwind-v4-migration
gh pr create --title "Add visual regression baseline" \
  --body "$(cat <<'EOF'
## Summary

- Adds a Playwright visual-regression harness covering 6 routes at 3 viewports plus modal, dropdown, toast, loading, empty and error states
- Commits 40 baseline PNGs captured before any component migration
- Every later migration phase re-runs `npm run visual` and must produce a zero-pixel diff

Uses Playwright's built-in `toHaveScreenshot` rather than the ImageMagick approach in the design spec, because the capture has to be re-runnable by CI and by a fresh contributor, not driven by hand.

## Test plan

- [x] `npm run visual` passes twice consecutively with no code changes between runs
- [x] 40 snapshots committed
- [x] `npm run check-unused` reports no new findings
EOF
)"
```

Note: `git push` and `gh` need `dangerouslyDisableSandbox: true` in this environment.

---

### Task 3: Computed-style extraction tool

**Files:**

- Create: `scripts/computed-style.mjs`

**Interfaces:**

- Consumes: a running app on `http://localhost:61234`.
- Produces: `node scripts/computed-style.mjs <route> <selector>` printing the element's resolved styles as JSON.

Bootstrap's rendered appearance is not readable from Bootstrap's source. `.form-control` alone is styled by Bootstrap's own rules, then overridden in `_modern-enhancements.scss:317`, which in turn resolves `var(--radius-base)`, `var(--modern-primary)` and `var(--transition-fast)` from a `:root` block. Reading any one of those three files gives the wrong answer. Every migration task below depends on reading the final computed value out of a live browser instead.

- [ ] **Step 1: Write the tool**

Create `scripts/computed-style.mjs`:

```js
import { chromium } from '@playwright/test'

const [route, selector] = process.argv.slice(2)

if (!route || !selector) {
  console.error('usage: node scripts/computed-style.mjs <route> <selector>')
  process.exit(1)
}

const PROPERTIES = [
  'display',
  'position',
  'width',
  'height',
  'min-width',
  'min-height',
  'margin-top',
  'margin-right',
  'margin-bottom',
  'margin-left',
  'padding-top',
  'padding-right',
  'padding-bottom',
  'padding-left',
  'font-family',
  'font-size',
  'font-weight',
  'line-height',
  'letter-spacing',
  'text-transform',
  'text-align',
  'color',
  'background-color',
  'border-top-width',
  'border-right-width',
  'border-bottom-width',
  'border-left-width',
  'border-color',
  'border-radius',
  'box-shadow',
  'opacity',
  'flex-direction',
  'align-items',
  'justify-content',
  'gap',
]

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.goto(`http://localhost:61234${route}`)
await page.waitForLoadState('networkidle')

const result = await page.$$eval(
  selector,
  (elements, properties) =>
    elements.map(el => {
      const computed = getComputedStyle(el)
      return Object.fromEntries(properties.map(p => [p, computed.getPropertyValue(p)]))
    }),
  PROPERTIES
)

if (result.length === 0) {
  console.error(`no element matched ${selector} on ${route}`)
  await browser.close()
  process.exit(1)
}

console.log(JSON.stringify(result[0], null, 2))
await browser.close()
```

- [ ] **Step 2: Verify it reports something real**

Run: `node scripts/computed-style.mjs /instruments '.btn-add-new'`
Expected: JSON with a non-empty `background-color`, a `border-radius`, and padding values.

- [ ] **Step 3: Commit**

```bash
git add scripts/computed-style.mjs
git commit -m "Add computed style extraction tool"
```

---

### Task 4: Migrate loading-spinner

**Files:**

- Modify: `ui/components/shared/loading-spinner.vue`

**Interfaces:**

- Consumes: `node scripts/computed-style.mjs` from Task 3; `npm run visual` from Task 1.
- Produces: no API change. Props and slots stay exactly as they are.

This is the smallest of the five primitives (78 lines, 33 of them scoped CSS) and exists to prove the migration loop before it is applied to a 421-line component.

- [ ] **Step 1: Read the component**

Read `ui/components/shared/loading-spinner.vue` in full. Its Bootstrap surface is `d-flex`, `justify-content-center`, `align-items-center`, `ms-2`, and `spinner-border`.

- [ ] **Step 2: Extract the real values**

Find a route that renders the spinner, then run:

```bash
node scripts/computed-style.mjs /instruments '.spinner-border'
```

Record `width`, `height`, `border-top-width`, `border-color`, and any `animation`. `spinner-border` is a Bootstrap keyframe animation; Tailwind's `tw:animate-spin` is not identical to it, so the animation must be preserved as a scoped rule rather than swapped for a utility.

- [ ] **Step 3: Translate**

Replace the layout classes only:

| Bootstrap                | Tailwind            |
| ------------------------ | ------------------- |
| `d-flex`                 | `tw:flex`           |
| `justify-content-center` | `tw:justify-center` |
| `align-items-center`     | `tw:items-center`   |
| `ms-2`                   | `tw:ml-2`           |

Keep `spinner-border` and its scoped CSS untouched. It is a Bootstrap component class, not a utility, and it dies in Phase 8 when the spinner gets its own keyframes. Migrating it here would produce a diff this phase is required to keep at zero.

- [ ] **Step 4: Verify no visual change**

Run: `npm run visual`
Expected: PASS, 40/40.

If any snapshot fails, open the diff PNG, compare against the values from Step 2, and correct the translation. Do not update the baseline.

- [ ] **Step 5: Verify tests and types**

Run:

```bash
npm test -- --run
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
```

Expected: tests pass; no new knip findings.

- [ ] **Step 6: Commit**

```bash
git add ui/components/shared/loading-spinner.vue
git commit -m "Migrate loading-spinner to Tailwind"
```

---

### Task 5: Migrate form-input

**Files:**

- Modify: `ui/components/shared/form-input.vue`

**Interfaces:**

- Consumes: Task 3's tool.
- Produces: no API change. `label`, `type`, `error`, `placeholder`, `options`, `step`, `min`, `max`, `id` props and the `defineModel` binding all stay.

- [ ] **Step 1: Extract the real values for all four states**

`.form-control` resolves through Bootstrap, then `_modern-enhancements.scss:317`, then `:root` custom properties. Run all four:

```bash
node scripts/computed-style.mjs /instruments '.form-control'
node scripts/computed-style.mjs /instruments '.form-select'
node scripts/computed-style.mjs /instruments '.form-label'
node scripts/computed-style.mjs /instruments '.invalid-feedback'
```

The invalid states need the modal open, so if `.invalid-feedback` matches nothing, trigger a validation error first by submitting the instrument form empty, then re-run.

- [ ] **Step 2: Record the focus ring separately**

`_modern-enhancements.scss:325` sets `outline: 2px solid rgba(67, 97, 238, 0.2)` with `outline-offset: 2px` and explicitly `box-shadow: none` on focus. The computed-style tool reads the resting state only. Reproduce focus with `tw:focus:outline-2 tw:focus:outline-signal-indigo/20 tw:focus:outline-offset-2` and verify visually — the spec's DESIGN.md rule is that focus is an outline, never a glow.

- [ ] **Step 3: Translate the template**

Rewrite the template using `tw:` utilities for the values recorded in Step 1. The structure does not change — only the class attributes. `mb-3` becomes `tw:mb-4` only if Step 1 reports `margin-bottom: 1rem`; Bootstrap's `mb-3` is 1rem while Tailwind's `mb-3` is 0.75rem, which is exactly the collision the `tw:` prefix exists to prevent. Read the number, do not assume it.

Keep `is-invalid` as an unstyled hook if any test queries it; check with:

```bash
grep -rn "is-invalid\|invalid-feedback\|form-control" --include='*.test.ts' ui
```

- [ ] **Step 4: Verify no visual change**

Run: `npm run visual`
Expected: PASS, 40/40.

The instrument modal snapshot is the one that exercises this component. If it fails, the diff PNG shows which of padding, border-radius, or font-size is off.

- [ ] **Step 5: Verify tests and types**

Run:

```bash
npm test -- --run
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add ui/components/shared/form-input.vue
git commit -m "Migrate form-input to Tailwind"
```

---

### Task 6: Migrate crud-layout

**Files:**

- Modify: `ui/components/shared/crud-layout.vue`

**Interfaces:**

- Consumes: Task 3's tool.
- Produces: no API change. `title`, `addButtonText`, `addButtonId`, `showAddButton` props; `add` and `title-click` emits; `toolbar`, `subtitle`, `subtitle-end`, `content`, `modals` slots.

- [ ] **Step 1: Extract the container width**

```bash
node scripts/computed-style.mjs /instruments '.container'
```

DESIGN.md pins this at `min(1350px, 91vw)`, which is a Bootstrap override, not stock Bootstrap. Tailwind's `container` is `width: 100%`. There is no Tailwind utility for this value, so it must become an arbitrary value: `tw:w-[min(1350px,91vw)] tw:mx-auto`. Confirm the number from the tool output before writing it.

- [ ] **Step 2: Extract the button and heading values**

```bash
node scripts/computed-style.mjs /instruments '.btn-add-new'
node scripts/computed-style.mjs /instruments 'h2'
```

- [ ] **Step 3: Translate**

| Bootstrap                                               | Tailwind                                                                      |
| ------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `container mt-3`                                        | `tw:w-[min(1350px,91vw)] tw:mx-auto tw:mt-4` (confirm `mt-3` = 1rem first)    |
| `d-flex justify-content-between align-items-start mb-4` | `tw:flex tw:justify-between tw:items-start tw:mb-6` (confirm `mb-4` = 1.5rem) |
| `flex-grow-1`                                           | `tw:grow`                                                                     |
| `d-flex justify-content-between align-items-center`     | `tw:flex tw:justify-between tw:items-center`                                  |
| `mb-0`                                                  | `tw:mb-0`                                                                     |
| `d-flex align-items-center gap-3`                       | `tw:flex tw:items-center tw:gap-4` (confirm `gap-3` = 1rem)                   |
| `d-none d-md-block`                                     | `tw:hidden tw:md:block`                                                       |

Leave `btn btn-primary btn-add-new` in place. Those are Bootstrap component classes styled by `_modern-enhancements.scss`, and replacing them now guarantees a non-zero diff. Buttons migrate in Phase 8 when their global rules are rewritten as a Tailwind component layer.

- [ ] **Step 4: Verify no visual change**

Run: `npm run visual`
Expected: PASS, 40/40. This component wraps `/instruments`, so a container-width error shows up as a whole-page horizontal shift.

- [ ] **Step 5: Verify tests and types**

Run:

```bash
npm test -- --run
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add ui/components/shared/crud-layout.vue
git commit -m "Migrate crud-layout to Tailwind"
```

---

### Task 7: Migrate skeleton-loader

**Files:**

- Modify: `ui/components/shared/skeleton-loader.vue`

**Interfaces:**

- Consumes: Task 3's tool.
- Produces: no API change.

This component is 149 lines with 89 lines of scoped CSS and only four Bootstrap tokens (`card`, `row`, `table`, `text-block`). Most of its CSS is its own shimmer animation, which stays.

- [ ] **Step 1: Read the component and identify which classes are Bootstrap**

Read `ui/components/shared/skeleton-loader.vue` in full. Distinguish Bootstrap classes from the component's own `skeleton-*` classes. Only the former migrate.

- [ ] **Step 2: Check the shimmer against reduced motion**

The repository has a global `prefers-reduced-motion` rule that forces animations to `0.01ms`. If your machine has Reduce Motion enabled, the shimmer will not animate and the loading snapshot will still be stable — but on a machine without it, the shimmer is volatile.

Confirm the loading snapshot is already masked or stable from Task 2 Step 7. If `state-loading.png` was one of the flaky snapshots, its shimmer region is already in `VOLATILE_SELECTORS` and nothing more is needed here.

- [ ] **Step 3: Translate the Bootstrap classes only**

Leave every `skeleton-*` class and all scoped CSS untouched.

- [ ] **Step 4: Verify no visual change**

Run: `npm run visual`
Expected: PASS, 40/40.

- [ ] **Step 5: Verify tests and types**

Run:

```bash
npm test -- --run
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add ui/components/shared/skeleton-loader.vue
git commit -m "Migrate skeleton-loader to Tailwind"
```

---

### Task 8: Migrate data-table

**Files:**

- Modify: `ui/components/shared/data-table.vue`
- Modify: `ui/components/shared/data-table.test.ts`

**Interfaces:**

- Consumes: Task 3's tool.
- Produces: no API change. The `ColumnDefinition` type is imported by `portfolio-summary.vue` and others as `import DataTable, { type ColumnDefinition } from './shared/data-table.vue'` — that export must survive unchanged.

This is the largest component in the phase at 421 lines, over the project's 400-line "refactor" threshold and well over the 200-line Vue guidance. Migrating it is in scope; splitting it is not — that would produce a structural diff this phase must keep at zero. Note the size in the PR body as follow-up work.

- [ ] **Step 1: Read the component**

Read `ui/components/shared/data-table.vue` in full. Its Bootstrap surface is `alert`, `btn`, `card`, `col`, `d-none`, `table`, `text-danger`, `text-end`, `text-success`.

- [ ] **Step 2: Extract the table values**

```bash
node scripts/computed-style.mjs /instruments 'table'
node scripts/computed-style.mjs /instruments 'table thead th'
node scripts/computed-style.mjs /instruments 'table tbody td'
```

DESIGN.md pins the header cell as uppercase `0.75rem`/600 with `0.05em` tracking in Muted Ink and calls it "the system's most recognizable gesture". Verify the computed output matches that before translating; if it does not, the current implementation already drifts from DESIGN.md and that drift must be preserved this phase and fixed in Phase 10.

- [ ] **Step 3: Check what the test asserts**

Run: `grep -n "classes\|toContain" ui/components/shared/data-table.test.ts`

Every Bootstrap class name the test asserts on either needs a replacement assertion or must be retained as an unstyled hook. Prefer updating the assertion to target behavior rather than a class name.

- [ ] **Step 4: Translate the layout and typography utilities**

Migrate `col`, `d-none`, `text-end`, `text-success`, `text-danger` to `tw:` equivalents. `text-success` and `text-danger` map to `tw:text-gain` and `tw:text-loss` **only if** Step 2 confirms the computed colors are `#21c55d` and `#dc3545`. If they resolve to `#22c55e` or `#dc2626`, that is DESIGN.md defect 2 — preserve the current value with an arbitrary utility this phase and record it for Phase 10.

Leave `table`, `card`, `btn`, and `alert` in place as component classes.

- [ ] **Step 5: Update the test**

Rewrite the assertions found in Step 3 against the new markup.

Run: `npm test -- --run ui/components/shared/data-table.test.ts`
Expected: PASS.

- [ ] **Step 6: Verify no visual change**

Run: `npm run visual`
Expected: PASS, 40/40. This component renders on `/`, `/transactions`, and `/instruments`, so it exercises 9 of the 18 route snapshots.

- [ ] **Step 7: Verify tests and types**

Run:

```bash
npm test -- --run
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add ui/components/shared/data-table.vue ui/components/shared/data-table.test.ts
git commit -m "Migrate data-table to Tailwind"
```

- [ ] **Step 9: Open the Phase 1 PR**

```bash
gh pr create --title "Migrate shared primitives to Tailwind" \
  --body "$(cat <<'EOF'
## Summary

- Migrates `loading-spinner`, `form-input`, `crud-layout`, `skeleton-loader` and `data-table` from Bootstrap utility classes to `tw:`-prefixed Tailwind utilities
- Bootstrap component classes (`btn`, `card`, `table`, `alert`, `spinner-border`) are deliberately left in place; they are styled by global rules and migrate in Phase 8
- Target values were read from the running app's computed styles, not from Bootstrap's source, because `.form-control` alone resolves through Bootstrap, `_modern-enhancements.scss`, and `:root` custom properties

## Test plan

- [x] `npm run visual` passes 40/40 with zero differing pixels
- [x] `npm test -- --run` passes
- [x] `data-table.test.ts` updated to assert on the new markup

## Follow-up

`data-table.vue` is 421 lines, above the project's 400-line threshold. Splitting it was out of scope here because a structural change would have produced a non-zero visual diff.
EOF
)"
```

---

### Task 9: Extract the platform filter

**Files:**

- Create: `ui/components/shared/platform-filter.vue`
- Modify: `ui/components/portfolio-summary.vue`
- Modify: `ui/components/transactions/transactions-view.vue`
- Modify: `ui/components/instruments/instruments-view.vue`
- Modify: `ui/components/etf/etf-breakdown.vue`

**Interfaces:**

- Consumes: `formatPlatformName` from `ui/utils/platform-utils`. Each call site keeps its own `usePlatformFilter()` call — the composable is not touched.
- Produces: `<PlatformFilter :available="string[]" :selected="string[]" :is-selected="(p: string) => boolean" @toggle="(p: string) => void" @toggle-all="() => void" />`

**The spec says 5 views; it is 4.** `grep -rn 'platform-filter-container' --include='*.vue' ui` returns four call sites: `portfolio-summary.vue:9`, `instruments-view.vue:11`, `transactions-view.vue:57`, `etf-breakdown.vue:24`. The fifth, `diversification-calculator.vue`, does not contain the filter at all. What it has is `allocation-table.vue:6`, which reuses the class names `platform-buttons` and `platform-btn` around a **different structure**, adds a `platform-btn-toggle-all` class the others do not have, and carries its own complete style block at `allocation-table.vue:956-990`. It is a different component that happens to share class names. **Do not extract it.** Folding it in would change its markup and its styling in the same step, which cannot produce a zero diff.

**This is not a pure markup move.** Three of the four call sites have scoped CSS that reaches into this markup, and Vue's scoping rules break two of them on extraction:

| Call site                   | Scoped rule                                                                                    | Survives extraction?                                                                                        |
| --------------------------- | ---------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `instruments-view.vue:231`  | `.platform-filter-container { gap; width; position }` and its `@media` override at `:343`      | **Yes.** Vue applies the parent's scope ID to a child component's root element, and this class is the root. |
| `etf-breakdown.vue:396`     | `.platform-btn.active { background: #0072b2 }` and `:hover` at `:401`                          | **No.** Nested inside the child; loses the parent scope ID.                                                 |
| `transactions-view.vue:333` | `@media (max-width: 768px) { .platform-btn { font-size: 0.75rem; padding: 0.375rem 0.5rem } }` | **No.** Same reason, and it fires at the 390px viewport we capture.                                         |

Both broken rules are repaired with `:deep()`. Without that, the ETF page's active buttons revert to the global blue and the mobile transactions buttons grow — a guaranteed non-zero diff in a phase that forbids one.

- [ ] **Step 1: Write the component**

Create `ui/components/shared/platform-filter.vue`, transcribed from `portfolio-summary.vue:9-26`:

```vue
<template>
  <div class="platform-filter-container">
    <div class="platform-buttons">
      <button
        v-for="platform in available"
        :key="platform"
        class="platform-btn"
        :class="{ active: isSelected(platform) }"
        type="button"
        @click="emit('toggle', platform)"
      >
        {{ formatPlatformName(platform) }}
      </button>
      <span class="platform-separator"></span>
      <button class="platform-btn" type="button" @click="emit('toggle-all')">
        {{ selected.length === available.length ? 'Clear All' : 'Select All' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatPlatformName } from '../../utils/platform-utils'

defineProps<{
  available: string[]
  selected: string[]
  isSelected: (platform: string) => boolean
}>()

const emit = defineEmits<{
  toggle: [platform: string]
  'toggle-all': []
}>()
</script>
```

The `platform-*` class names stay and remain styled by `ui/styles/_platform-filter.scss`. Migrating them to Tailwind here would produce a visual diff; they migrate in Phase 8 with the rest of the global styles.

There is deliberately no `v-if` inside the component and no `minPlatforms` prop. The four call sites disagree on the threshold — three use `> 0`, `etf-breakdown.vue:24` uses `> 1` — so the guard stays at the call site on the `<PlatformFilter>` tag itself, where each view already expresses it. A prop would be configuration invented to paper over a two-value difference.

- [ ] **Step 2: Convert the first call site**

In `ui/components/portfolio-summary.vue`, replace lines 9–26 with:

```vue
<PlatformFilter
  v-if="availablePlatforms.length > 0"
  class="mt-2 mb-3"
  :available="availablePlatforms"
  :selected="selectedPlatforms"
  :is-selected="isPlatformSelected"
  @toggle="togglePlatform"
  @toggle-all="toggleAllPlatforms"
/>
```

Add the import next to the existing ones:

```ts
import PlatformFilter from './shared/platform-filter.vue'
```

Vue applies a non-prop `class` to the component's root element, so `mt-2 mb-3` lands on `.platform-filter-container` exactly where it was.

- [ ] **Step 3: Verify the first call site alone**

Run: `npm run visual`
Expected: PASS, 40/40.

Converting one view before the other three means a failure here points at the component, not at one of four call sites. `portfolio-summary.vue` is the right first one because it is the only call site with no scoped CSS touching this markup — it isolates the extraction from the `:deep()` problem.

- [ ] **Step 4: Convert instruments-view**

In `ui/components/instruments/instruments-view.vue`, replace lines 11–29 with the same element, using this view's own threshold and spacing:

```vue
<PlatformFilter
  v-if="availablePlatforms.length > 0"
  class="mt-2"
  :available="availablePlatforms"
  :selected="selectedPlatforms"
  :is-selected="isPlatformSelected"
  @toggle="togglePlatform"
  @toggle-all="toggleAllPlatforms"
/>
```

Import path from this directory is `../shared/platform-filter.vue`.

Leave the `.platform-filter-container` rules at `:231` and `:343` exactly as they are. They target the child's root element and keep working.

- [ ] **Step 5: Convert transactions-view and repair its mobile rule**

In `ui/components/transactions/transactions-view.vue`, replace lines 57–73 with the element, no extra class:

```vue
<PlatformFilter
  v-if="availablePlatforms.length > 0"
  :available="availablePlatforms"
  :selected="selectedPlatforms"
  :is-selected="isPlatformSelected"
  @toggle="togglePlatform"
  @toggle-all="toggleAllPlatforms"
/>
```

Then change the mobile rule at `:333` from:

```css
.platform-btn {
  font-size: 0.75rem;
  padding: 0.375rem 0.5rem;
}
```

to:

```css
:deep(.platform-btn) {
  font-size: 0.75rem;
  padding: 0.375rem 0.5rem;
}
```

`:deep(.platform-btn)` compiles to `[data-v-x] .platform-btn`, which matches any descendant of this view's root regardless of scope ID. That covers both the extracted buttons and this view's own `.platform-btn` elements at `:31` and `:49`, which stay in the parent. One rule, both cases.

Leave `.date-actions-row .platform-btn` at `:282` alone. It targets the dropdown row at `:31`/`:49`, which is not part of the extracted markup.

- [ ] **Step 6: Convert etf-breakdown and repair its active-state override**

In `ui/components/etf/etf-breakdown.vue`, replace lines 24–40 with the element, preserving this view's `> 1` threshold:

```vue
<PlatformFilter
  v-if="availablePlatforms.length > 1"
  class="mt-2"
  :available="availablePlatforms"
  :selected="selectedPlatforms"
  :is-selected="isPlatformSelected"
  @toggle="togglePlatform"
  @toggle-all="toggleAllPlatforms"
/>
```

Then change the two rules at `:396` and `:401` from:

```css
.platform-btn.active {
  background: #0072b2;
  border-color: #0072b2;
}

.platform-btn.active:hover {
  background: #005a8c;
  border-color: #005a8c;
}
```

to:

```css
:deep(.platform-btn.active) {
  background: #0072b2;
  border-color: #0072b2;
}

:deep(.platform-btn.active:hover) {
  background: #005a8c;
  border-color: #005a8c;
}
```

This override exists because the ETF page uses a different accent blue than the global `.platform-btn.active`. If it is dropped, every active platform button on `/etf-breakdown` changes color and the route snapshot fails.

- [ ] **Step 7: Verify all four call sites**

Run: `npm run visual`
Expected: PASS, 40/40.

The two snapshots most likely to catch a mistake here are `route-etf-breakdown-desktop.png` (the `#0072b2` override) and `route-transactions-mobile.png` (the font-size rule). If either fails, the corresponding `:deep()` is missing or misspelled.

- [ ] **Step 8: Verify tests and types**

Run:

```bash
npm test -- --run
npm run lint-format
git checkout -- ui/models/generated/domain-models.ts
```

Expected: PASS.

`instruments-view.test.ts` is one of the ten Bootstrap-coupled test files. If it asserts on the filter markup rather than on behavior, update it to assert on the emitted event or on `PlatformFilter`'s props.

- [ ] **Step 9: Confirm the duplication is gone**

Run:

```bash
grep -rn 'platform-filter-container' --include='*.vue' ui
```

Expected: exactly three matches — the component's own template, plus the two surviving CSS rules in `instruments-view.vue` at `:231` and `:343`. No remaining `<div class="platform-filter-container">` in any view template.

- [ ] **Step 10: Commit**

```bash
git add ui/components/shared/platform-filter.vue ui/components/portfolio-summary.vue \
  ui/components/transactions/transactions-view.vue ui/components/instruments/instruments-view.vue \
  ui/components/etf/etf-breakdown.vue
git commit -m "Extract shared platform filter component"
```

- [ ] **Step 11: Open the Phase 2 PR**

```bash
gh pr create --title "Extract shared platform filter component" \
  --body "$(cat <<'EOF'
## Summary

- Extracts the platform filter markup, duplicated across 4 views, into `ui/components/shared/platform-filter.vue`
- `usePlatformFilter()` is unchanged; each view keeps its own call
- Per-view spacing passes through as a non-prop `class`, and the differing visibility thresholds stay as `v-if` at the call site
- Two scoped rules that reached into the markup are now `:deep()`: the ETF page's `#0072b2` active-button override and the mobile font-size rule in transactions. Without these the extraction silently changes both pages.
- `allocation-table.vue` reuses the same class names around different markup with its own style block, so it is deliberately left alone
- `platform-*` class names and `_platform-filter.scss` are untouched; they migrate in Phase 8

This unblocks Phase 7, where these views migrate — without it the same markup would be translated four times.

## Test plan

- [x] `npm run visual` passes 40/40 with zero differing pixels
- [x] `npm test -- --run` passes
- [x] No `<div class="platform-filter-container">` remains in any view template

EOF
)"
```

---

## What this plan deliberately leaves alone

Recorded here so a later phase does not treat these as oversights:

- **Bootstrap component classes** (`btn`, `card`, `table`, `alert`, `spinner-border`, `modal`, `platform-btn`) stay. They are styled by global rules in `_modern-enhancements.scss` and `_platform-filter.scss`. Replacing a component class without simultaneously rewriting its global rule guarantees a non-zero diff, which is precisely what Phases 1–9 forbid. They migrate in Phase 8.
- **`data-table.vue`'s 421 lines.** Splitting it is a structural change with a visual risk and belongs after the migration.
- **The `is-invalid` / `invalid-feedback` validation classes** in `form-input.vue`, for the same reason as component classes.
- **`allocation-table.vue`'s platform buttons.** It reuses `platform-buttons` and `platform-btn` around different markup, adds `platform-btn-toggle-all`, and ships its own style block at `:956-990`. It is not a fifth copy of the shared filter and does not fold into it. It migrates with the rest of the diversification components in Phase 3.

## Notes for the next plan

Phases 3–10 should be planned only after Phase 1 lands, because two open questions get answered by it:

1. **How much hand-tuning does a translation actually need?** If Tasks 4–8 pass `npm run visual` on the first try, later phases can batch several components per task. If each needs three or four correction rounds, tasks must stay one-component-each.
2. **Do the `text-success` / `text-danger` colors resolve to the DESIGN.md tokens or to the drifted ones?** Task 8 Step 4 answers this, and the answer determines whether Phase 10's defect 2 is a two-line change or a sweep.
