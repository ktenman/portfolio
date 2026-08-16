# Tailwind Migration Phase 7 (PR 1: App Shell) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Translate the 24 remaining Bootstrap utility classes in the app shell (`app.vue`, `nav-bar.vue`, `portfolio-actions.vue`, `etf-breakdown-header.vue`, `instruments-view.vue`) to prefixed Tailwind v4 utilities with a zero-pixel screenshot diff.

**Architecture:** Pure mechanical class substitution in templates. No DOM restructuring, no scoped-CSS edits, no component-class removal. One new `@theme` colour token is required because the `text-muted` convention recorded in issue #1673 is factually wrong (measured below). Each file is committed separately and gated by the Playwright pixel harness.

**Tech Stack:** Vue 3.5 SFCs, Tailwind v4 (`tw:` prefix, utilities-only — **no preflight**), Bootstrap 5.3 (still loaded), Playwright (`maxDiffPixels: 0`), Vitest.

## Global Constraints

- `tw:` prefix on every utility until Phase 9.
- Utilities are scanned as **literal** strings from `@source '../**/*.{vue,ts}'` — a computed/concatenated class name will not be emitted.
- Spacing scale is `0.25rem`: Bootstrap `mb-3`=1rem→`tw:mb-4`, `mb-4`=1.5rem→`tw:mb-6`, `py-2`=0.5rem→`tw:py-2`, `mt-2`=0.5rem→`tw:mt-2`, `me-1`=0.25rem→`tw:mr-1`.
- Breakpoints already match Bootstrap in `@theme` (sm 576, md 768, lg 992, xl 1200, 2xl 1400).
- **Bootstrap utilities carry `!important`.** Add `!` only when a scoped or global rule of higher specificity sets the same property. Every site in this PR was checked; **none need `!`** (justifications are inline per task).
- Do not restructure DOM during translation.
- Do not touch component classes (`.btn`, `.navbar`, `.card`, `.form-control`, `.table`) — those are Phase 8/9.
- Any non-zero pixel diff is a regression: fix it or record it as explicitly accepted drift.
- Known pre-existing failure: `state loading skeleton` (desktop, 13658 px) fails on `main` too. Ignore it; do not re-record its baseline.
- Commit subjects: uppercase imperative verb, ≤50 chars, no `feat:`/`fix:`/`chore:` prefixes, no AI attribution.
- PR body ends with `Refs #1662` — **not** `Closes`.
- Never `git add -A`; stage explicit paths only.
- `npm run lint-format` strips `ui/models/generated/domain-models.ts` via `eslint --fix` — revert it with `git checkout ui/models/generated/domain-models.ts`.

## Measured Facts (do not re-derive)

Probed in Chromium against the running dev server on 2026-08-11:

| Bootstrap class    | Computed value                 | Tailwind equivalent                                            |
| ------------------ | ------------------------------ | -------------------------------------------------------------- |
| `text-muted`       | `rgba(33, 37, 41, 0.75)`       | **none exists** — needs a new token                            |
| `tw:text-gray-600` | `rgb(108, 117, 125)`           | —                                                              |
| `bg-light`         | `rgb(248, 249, 250)`           | `tw:bg-gray-100` (`--color-gray-100: #f8f9fa`)                 |
| `border-bottom`    | `1px solid rgb(222, 226, 230)` | `tw:border-b tw:border-gray-300` (`--color-gray-300: #dee2e6`) |

**Issue #1673 states `text-muted` → `tw:text-gray-600` (both `#6c757d`). That is wrong.** `.text-muted` compiles to `color: var(--bs-secondary-color) !important` and `--bs-secondary-color` is `rgba(33,37,41,.75)`; the `$text-muted: #6c757d` override at `ui/styles/_bootstrap-overrides.scss:23` is dead in Bootstrap 5.3 (the utility API hardcodes `var(--bs-secondary-color)`). Task 1 introduces the correct token.

Tailwind v4 declares `--tw-border-style: solid` via `@property`, so `tw:border-b` yields a solid border even without preflight — no `tw:border-solid` companion is needed. Precedent: `ui/components/shared/currency-split-card.vue:4` (`tw:border tw:border-hairline`) renders a visible border today.

Container translation is already proven in `ui/components/shared/crud-layout.vue:2`:
`container` → `tw:mx-auto tw:w-full tw:max-w-[min(1350px,91vw)] tw:px-3`
(`.container` and `.container-fluid` are identical in this app — `ui/styles/app.scss:17-19` caps both at `min(1350px, 91vw)`.)

## File Structure

| File                                             | Change                                                                    |
| ------------------------------------------------ | ------------------------------------------------------------------------- |
| `ui/styles/theme.css`                            | Add one `@theme` colour token (`--color-body-secondary`)                  |
| `ui/app.vue`                                     | 9 utilities: shell flex column, main grow, content container, footer      |
| `ui/components/nav-bar.vue`                      | 4 utilities: `bg-white`, `border-bottom`, `container-fluid`, `text-muted` |
| `ui/components/portfolio/portfolio-actions.vue`  | 6 utilities: header flex row + spinner margin                             |
| `ui/components/etf/etf-breakdown-header.vue`     | 2 utilities: `mb-4`, `text-muted`                                         |
| `ui/components/instruments/instruments-view.vue` | 3 utilities: `mt-2`, `d-none`, `d-md-inline`                              |

Out of scope for this PR (PR 2 of Phase 7): `portfolio-summary.vue`, `transactions-view.vue`, `etf-breakdown.vue`, `calculator.vue`, `diversification-calculator.vue` (77 occurrences). `instrument-form.vue` and `transaction-form.vue` already have zero Bootstrap utilities.

## Preconditions

The pixel gate has **no `webServer`** — it needs an external Vite on port 61234.

```bash
npm run dev:ui        # leave running for the whole plan
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:61234   # expect 200
```

Branch is `feature/1673-tailwind-phase-7-app-shell`, cut from `main`.

---

### Task 1: Add the `body-secondary` colour token

**Files:**

- Modify: `ui/styles/theme.css:23`

**Interfaces:**

- Produces: `--color-body-secondary` → utility `tw:text-body-secondary`, consumed by Tasks 3 and 5.

- [ ] **Step 1: Add the token**

In `ui/styles/theme.css`, insert one line after `--color-ink-muted: #6b7280;`:

```css
--color-ink: #212529;
--color-ink-muted: #6b7280;
--color-body-secondary: rgba(33, 37, 41, 0.75);
--color-hairline: #e2e8f0;
```

- [ ] **Step 2: Commit**

```bash
git add ui/styles/theme.css
git commit -m "Add body-secondary colour token"
```

Note: `tw:text-body-secondary` is not emitted until a `.vue` file references it literally (Task 3). A build check here would show nothing — that is expected, not a failure.

---

### Task 2: Migrate `ui/app.vue`

**Files:**

- Modify: `ui/app.vue:5,7,8,16`

**Interfaces:**

- Consumes: nothing.
- Produces: nothing (leaf template change).

Specificity check: no scoped rule in `app.vue` targets `min-height`, `flex`, `padding` or `background-color` on these four elements — `.auth-loading` / `.auth-spinner` are the only scoped selectors. No `!` needed.

- [ ] **Step 1: Rewrite the four class attributes**

Replace lines 5-18 of `ui/app.vue` with:

```vue
  <div v-else class="tw:flex tw:min-h-screen tw:flex-col">
    <NavBar />
    <main class="tw:grow">
      <div class="tw:mx-auto tw:w-full tw:max-w-[min(1350px,91vw)] tw:px-3 tw:py-2">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>
    <footer class="tw:bg-gray-100 tw:py-2 tw:text-center">
      <small>&copy; {{ currentYear }} Portfolio Manager</small>
    </footer>
```

Mapping: `d-flex`→`tw:flex`, `flex-column`→`tw:flex-col`, `min-vh-100`→`tw:min-h-screen`, `flex-grow-1`→`tw:grow`, `container-fluid`→`tw:mx-auto tw:w-full tw:max-w-[min(1350px,91vw)] tw:px-3`, `py-2`→`tw:py-2`, `bg-light`→`tw:bg-gray-100`, `text-center`→`tw:text-center`.

- [ ] **Step 2: Run the pixel gate on every route**

The shell wraps all six routes, so all must be checked.

Run: `npx playwright test routes.spec.ts`
Expected: 18 passed.

- [ ] **Step 3: Commit**

```bash
git add ui/app.vue
git commit -m "Migrate app shell to Tailwind utilities"
```

---

### Task 3: Migrate `ui/components/nav-bar.vue`

**Files:**

- Modify: `ui/components/nav-bar.vue:2,3,16`

**Interfaces:**

- Consumes: `--color-body-secondary` from Task 1.

The `container-fluid` here is the one real trap. Bootstrap applies an extra rule to a container that is a direct child of `.navbar`:

```css
.navbar > .container-fluid {
  display: flex;
  flex-wrap: inherit;
  align-items: center;
  justify-content: space-between;
}
```

`.navbar` is `flex-wrap: wrap`, and `.navbar-expand` overrides it to `flex-wrap: nowrap`, so `inherit` resolves to `nowrap`. All four declarations must be reproduced explicitly or the nav collapses.

Specificity check: `.build-info-text` (scoped) sets only `padding`, and `.build-info` sets only font/padding/display/align/margin/white-space — no `color`. So `text-muted` is live here and the replacement needs no `!`.

- [ ] **Step 1: Rewrite the nav and container class attributes**

Replace lines 2-3 of `ui/components/nav-bar.vue` with:

```vue
  <nav class="navbar navbar-expand navbar-light tw:border-b tw:border-gray-300 tw:bg-white navbar-sticky">
    <div
      class="tw:mx-auto tw:flex tw:w-full tw:max-w-[min(1350px,91vw)] tw:flex-nowrap tw:items-center tw:justify-between tw:px-3"
    >
```

- [ ] **Step 2: Rewrite the build-info span**

Replace line 16 of `ui/components/nav-bar.vue`:

```vue
            <span class="tw:text-body-secondary build-info-text">
```

- [ ] **Step 3: Run the pixel gate on every route**

The navbar is on every route.

Run: `npx playwright test routes.spec.ts`
Expected: 18 passed.

If the bottom hairline vanishes, `tw:border-b` failed to emit a border-style — confirm with:
`npm run build && grep -o '\.tw\\:border-b{[^}]*}' dist/assets/*.css`

- [ ] **Step 4: Commit**

```bash
git add ui/components/nav-bar.vue
git commit -m "Migrate nav bar to Tailwind utilities"
```

---

### Task 4: Migrate `ui/components/portfolio/portfolio-actions.vue`

**Files:**

- Modify: `ui/components/portfolio/portfolio-actions.vue:2,3,9`

Specificity check: this SFC has no `<style>` block. `.btn-spinner` (`ui/styles/_modern-enhancements.scss:477`) sets `display`, `width`, `height`, `border`, `border-radius`, `animation`, `vertical-align` — **no margin**. So `tw:mr-1` needs no `!`.

- [ ] **Step 1: Rewrite the template**

Replace lines 2-11 of `ui/components/portfolio/portfolio-actions.vue` with:

```vue
  <div class="tw:mb-6 tw:flex tw:items-center tw:justify-between">
    <h2 class="tw:mb-0">Portfolio Summary</h2>
    <button
      class="btn btn-ghost btn-sm btn-secondary"
      @click="$emit('recalculate')"
      :disabled="isRecalculating || isLoading"
    >
      <span
        v-if="isRecalculating"
        class="btn-spinner tw:mr-1"
        role="status"
        aria-hidden="true"
      ></span>
      {{ isRecalculating ? 'Recalculating...' : 'Recalculate Data' }}
    </button>
```

Mapping: `d-flex`→`tw:flex`, `justify-content-between`→`tw:justify-between`, `align-items-center`→`tw:items-center`, `mb-4` (1.5rem)→`tw:mb-6`, `mb-0`→`tw:mb-0`, `me-1`→`tw:mr-1`.

- [ ] **Step 2: Run the pixel gate on the summary route**

Run: `npx playwright test routes.spec.ts -g "route summary"`
Expected: 3 passed.

The spinner only renders while recalculating, which no capture exercises; its margin is verified by the class appearing in the built CSS:
`npm run build && grep -o '\.tw\\:mr-1{[^}]*}' dist/assets/*.css`
Expected: `.tw\:mr-1{margin-right:calc(var(--spacing) * 1)}`

- [ ] **Step 3: Commit**

```bash
git add ui/components/portfolio/portfolio-actions.vue
git commit -m "Migrate portfolio actions to Tailwind"
```

---

### Task 5: Migrate `ui/components/etf/etf-breakdown-header.vue`

**Files:**

- Modify: `ui/components/etf/etf-breakdown-header.vue:2,4`

**Interfaces:**

- Consumes: `--color-body-secondary` from Task 1.

Specificity check: scoped `.page-subtitle` sets `font-size` and `margin` only — no `color`. `.page-header` sets `display`, `justify-content`, `align-items`, `gap`, `flex-wrap` — no `margin-bottom`. No `!` needed.

- [ ] **Step 1: Rewrite the header and subtitle class attributes**

Replace lines 2-6 of `ui/components/etf/etf-breakdown-header.vue` with:

```vue
<div class="page-header tw:mb-6">
    <div>
      <p class="page-subtitle tw:text-body-secondary">
        {{ getDescription() }}
      </p>
    </div>
```

- [ ] **Step 2: Run the pixel gate on the ETF route**

Run: `npx playwright test routes.spec.ts -g "route etf-breakdown"`
Expected: 3 passed.

A non-zero diff on the subtitle text means the `--color-body-secondary` value is off — re-probe rather than adjust the baseline.

- [ ] **Step 3: Commit**

```bash
git add ui/components/etf/etf-breakdown-header.vue
git commit -m "Migrate ETF breakdown header to Tailwind"
```

---

### Task 6: Migrate `ui/components/instruments/instruments-view.vue`

**Files:**

- Modify: `ui/components/instruments/instruments-view.vue:12,21`
- Test: `ui/components/instruments/instruments-view.test.ts` (assertions reference `.platform-btn`, `.platform-separator`, `.toggle-container`, `.toggle-label` only — no change expected; run it to confirm)

Specificity check: scoped `.period-label` sets `font-size`, `font-weight`, `color`, `margin` — no `display`. `tw:hidden` / `tw:md:inline` need no `!`. Precedent for the responsive pair: `ui/components/shared/crud-layout.vue:13` uses `tw:hidden tw:md:block`.

- [ ] **Step 1: Rewrite the platform-filter margin**

Replace line 12 of `ui/components/instruments/instruments-view.vue`:

```vue
class="tw:mt-2"
```

- [ ] **Step 2: Rewrite the period label**

Replace line 21 of `ui/components/instruments/instruments-view.vue`:

```vue
<label class="period-label tw:hidden tw:md:inline">Period:</label>
```

- [ ] **Step 3: Run the unit test**

Run: `npm test -- --run instruments-view`
Expected: PASS, no assertion touches the migrated classes.

- [ ] **Step 4: Run the pixel gate on the instruments route**

Covers mobile (label hidden) and desktop/tablet (label inline).

Run: `npx playwright test routes.spec.ts -g "route instruments"`
Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add ui/components/instruments/instruments-view.vue
git commit -m "Migrate instruments view to Tailwind"
```

---

### Task 7: Verify the whole PR and ship it

**Files:**

- No source changes expected.

- [ ] **Step 1: Confirm no Bootstrap utilities remain in the five migrated files**

```bash
grep -nE 'class="[^"]*\b(d-flex|d-none|d-md-inline|flex-column|flex-grow-1|min-vh-100|container-fluid|container|bg-light|bg-white|border-bottom|text-center|text-muted|justify-content-between|align-items-center|mb-4|mb-0|me-1|mt-2|py-2)\b' \
  ui/app.vue ui/components/nav-bar.vue ui/components/portfolio/portfolio-actions.vue \
  ui/components/etf/etf-breakdown-header.vue ui/components/instruments/instruments-view.vue
```

Expected: no output.

- [ ] **Step 2: Run the full pixel gate**

Run: `npx playwright test`
Expected: all pass except the known pre-existing `state loading skeleton` (desktop, ~13658 px).

- [ ] **Step 3: Run unit tests and lint**

```bash
npm test -- --run
npm run lint-format
git checkout ui/models/generated/domain-models.ts
```

Expected: tests pass; lint-format clean. Knip may report `bootstrap` under "Remove from ignoreDependencies" — that is the untracked-worktree false positive, ignore it; CI is authoritative.

- [ ] **Step 4: Push and open the PR**

```bash
git push -u origin feature/1673-tailwind-phase-7-app-shell
gh pr create --title "Migrate UI to Tailwind v4 (phase 7: app shell)" --body "$(cat <<'EOF'
## Summary

- Translate the 24 remaining Bootstrap utility classes in the app shell to prefixed Tailwind v4 utilities
- Add a `--color-body-secondary` theme token: `text-muted` resolves to `rgba(33,37,41,.75)`, not `#6c757d` as issue #1673 states, so `tw:text-gray-600` would have shifted the colour
- No DOM restructuring, no scoped-CSS edits, no component classes touched

Files: `app.vue`, `nav-bar.vue`, `portfolio-actions.vue`, `etf-breakdown-header.vue`, `instruments-view.vue`.
The five grid-heavy route components (77 occurrences) follow in PR 2.

## Test plan

- [ ] `npx playwright test` — zero pixel diff across 6 routes × 3 viewports (pre-existing `state loading skeleton` desktop failure unchanged)
- [ ] `npm test -- --run`
- [ ] `npm run lint-format`

Refs #1662
EOF
)"
```

Both commands need `dangerouslyDisableSandbox: true`.

- [ ] **Step 5: Watch CI**

Run: `gh pr checks --watch`
Expected: all green.

---

## Self-Review

**Spec coverage:** Issue #1673 lists 12 files; this PR covers 5 of them (the app shell). `instrument-form.vue` and `transaction-form.vue` have zero Bootstrap utilities and need no work. The remaining 5 grid-heavy route components are PR 2 with its own plan. Exit criterion "screenshot diff = 0" is Task 7 Step 2; "no Bootstrap utility classes remain" is scoped to these 5 files in Task 7 Step 1; `instruments-view.test.ts` is Task 6 Step 3.

**Placeholder scan:** every code step carries literal markup; no "TBD", no "similar to Task N".

**Type consistency:** the only cross-task interface is the `--color-body-secondary` token, spelled identically in Tasks 1, 3 and 5.

**Known deviation from the issue:** issue #1673's `text-muted` → `tw:text-gray-600` convention is not used, for the measured reason above. Flag this on the parent issue #1662 so PR 2 and later phases do not repeat it.

**Noted, not fixed (out of scope):** `ui/components/instruments/instrument-table.vue:249` already carries `tw:text-gray-600` where `text-muted` used to be (shipped in Phase 3, commit `7226745d`, with no baseline re-record). By the measurement above that changed the desktop instrument-symbol colour from `rgba(33,37,41,.75)` to `#6c757d`. Leave it; raise it separately.
