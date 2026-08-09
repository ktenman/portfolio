# Bootstrap 5.3 + Sass → Tailwind v4 + native platform

Design spec for migrating the portfolio UI off Bootstrap and Sass. Issue #1662.

## Current state

Measured 2026-08-07 on `feature/1662-tailwind-v4-migration` at commit `a0df4ea4`.

|                                  | count             |
| -------------------------------- | ----------------- |
| `.vue` components                | 36                |
| Bootstrap-dependent components   | 29                |
| components already clean         | 7                 |
| scoped `<style>` lines in `.vue` | 2,689             |
| global SCSS lines (12 files)     | 1,456             |
| Bootstrap JS import sites        | 6 source + 2 test |
| `data-bs-*` attributes           | 7                 |
| routes                           | 6 + catch-all     |
| test files / tests               | 52 / 909          |
| test files coupled to Bootstrap  | 10                |

The foundation phase has already shipped: Tailwind v4.3.3 builds via `@tailwindcss/vite`, DESIGN.md tokens are wired into `ui/styles/theme.css`, and `currency-split-card.vue` is migrated as proof.

### Why utilities are prefixed `tw:`

Bootstrap and Tailwind share class names with incompatible meanings. Tailwind scans `.vue` files, finds Bootstrap class names, and generates its own utilities under those names — which land later in the cascade and win.

| class       | Bootstrap                          | Tailwind            |
| ----------- | ---------------------------------- | ------------------- |
| `p-3`       | 1rem                               | 0.75rem             |
| `p-5`       | 3rem                               | 1.25rem             |
| `col-6`     | `width: 50%`                       | `grid-column: 6`    |
| `container` | `min(1350px, 91vw)`                | `width: 100%`       |
| `border`    | `1px solid var(--bs-border-color)` | `border-width: 1px` |

Unprefixed coexistence silently shrinks every `-3`/`-4`/`-5` spacing in the app and breaks the grid. The `tw:` prefix eliminates this. It is removed in Phase 9 once Bootstrap is gone.

### Structural findings

**`_modern-enhancements.scss` is a Bootstrap skin, not a design layer.** Its header says "Progressively enhances the existing Bootstrap setup", and its 887 lines override `.btn`, `.card`, `.form-control`, `.table`, `.navbar`. When components stop emitting Bootstrap class names, most of it becomes dead code rather than something to translate. Only its `:root` custom properties migrate, and those are already in `theme.css`. This is why the global SCSS sweep is late (Phase 8), not early — it is a consequence of component migration, not a prerequisite.

**All 6 modal components share one skeleton.** `confirm-dialog`, `instrument-modal`, `xirr-windows-modal`, `annual-windows-modal`, `logo-replacement-modal`, and `config-dialog` all use identical `modal / modal-dialog / modal-content / modal-header / modal-title / btn-close` markup. Phase 5 extracts a single `<dialog>`-based shell instead of migrating the same markup six times.

**Bootstrap leaks into `ui/utils/`, not just components and styles.** `ui/utils/style-classes.ts` is a 131-line registry of Bootstrap class-name strings covering layout, spacing, buttons, forms, tables, modals, badges, and spinners. Nothing imports it except `ui/utils/formatters.ts`, which uses exactly three lookups — all of them `text.success` / `text.danger` — inside `getProfitClass`, `getAmountClass`, and `formatPriceChange`. The rest of the registry is dead. It is deleted in Phase 3 and the three call sites take the token colors directly, since `gain` and `loss` are already in `theme.css`.

`formatPriceChange` additionally returns an HTML string (`<span class="...">…</span>`) rendered through the app's only `v-html`, at `instrument-table.vue:268`. Phase 3 replaces it with a value/class pair bound normally, removing the `v-html` along with the Bootstrap class.

**Sass leaves when Bootstrap leaves.** `_bootstrap-overrides.scss` contains only Sass variables (`$primary`, `$spacers`, `$grid-breakpoints`, `$enable-dark-mode`) and `framework/bootstrap.scss` imports Bootstrap's Sass source. Sass exists in this project solely because Bootstrap's customization API is Sass. There is no separate SCSS→CSS migration; converting files the migration deletes would be wasted work. Sass features actually in use across all 1,456 lines: 6 mixins, 6 functions, 4 `map.get`, 2 `darken()`, 1 `@if`.

## Goals

- Remove `bootstrap`, `@types/bootstrap`, and `sass` from the dependency tree.
- Preserve the current appearance exactly through Phases 1–9.
- Replace Bootstrap's JS with native platform features, adding zero dependencies.
- Fix the defects DESIGN.md documents, in a separate final phase.

## Non-goals

- No redesign. DESIGN.md is the contract for what must survive.
- No new component library. Reka UI was considered and rejected; the platform covers all three primitives this app uses.
- No unrelated refactoring. The platform-filter extraction (Phase 2) and modal-shell extraction (Phase 5) are in scope only because they are prerequisites for migrating those components without duplicating work 5 and 6 times respectively.
- No dark mode. `[data-bs-theme='dark']` is defined at `_modern-enhancements.scss:40` but nothing sets the attribute and `$enable-dark-mode: false`. It is dormant, and stays dormant.

## Decisions

| Decision                 | Choice                                                 | Rationale                                                                                                                                                                           |
| ------------------------ | ------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Verification             | Screenshot baseline captured first, diffed every phase | 4,145 lines of CSS change with no existing visual regression coverage. Logic tests cannot catch a layout break.                                                                     |
| Bootstrap JS replacement | Native `<dialog>`, Popover API, Vue toast component    | All three usages are thin. The platform provides focus trap, Esc, backdrop, and light-dismiss for free. Zero new dependencies, less code than today.                                |
| DESIGN.md defect fixes   | Separate final phase, after migration                  | Keeps every phase's diff meaningful: during migration any diff is a regression. Mixing intentional change in would destroy the signal the baseline exists to provide.               |
| Base resets              | Enable Tailwind preflight at Phase 9                   | Replaces Bootstrap's reboot with the maintained framework-native reset. The app defines only 3 global selectors (`body`, `#app`, `:root`), so it genuinely depends on reboot today. |
| Git                      | One PR per phase off the issue branch                  | Each phase is independently reviewable and revertable, with its screenshot diff as evidence.                                                                                        |
| Migration order          | Bottom-up, leaf-first                                  | `data-table.vue` feeds 4 table components. Leaves have no dependents, so mistakes stay contained and the app is never half-broken.                                                  |

### Rejected alternatives

**Route-by-route migration.** Ships a visibly complete screen per phase, which reads better in a PR. Rejected because shared components are used across multiple routes, so `data-table` would be migrated under `/instruments` and reworked under `/transactions`. Rework by construction.

**Global-CSS-first.** Rejected on the finding above: `_modern-enhancements.scss` _is_ the current appearance of `.btn`/`.card`/`.table`. Deleting it before components migrate breaks everything simultaneously.

**Reka UI.** A consistent accessible primitive set with room to grow into select/combobox/tooltip. Rejected as YAGNI: this app needs Modal, Toast, and Dropdown, and the platform covers all three. Revisit only if a component needs a primitive the platform lacks.

## Verification strategy

### Baseline (Phase 0)

Captured from `main`, before any migration commit, so it reflects true pre-migration appearance. The already-shipped foundation commit changed `currency-split-card` slightly (hairline `#e0e0e0`→`#e2e8f0`, row text `#1a1a1a`→`#212529` via `tw:text-ink`, shadow, letter-spacing), so capturing from the current branch would bake that drift into the reference.

Viewports are 390px (mobile), 768px (tablet), and 1440px (desktop). The capture rule:

| Surface                                                                                             | Viewports | Captures |
| --------------------------------------------------------------------------------------------------- | --------- | -------- |
| 6 routes: `/`, `/transactions`, `/instruments`, `/etf-breakdown`, `/diversification`, `/calculator` | all 3     | 18       |
| 7 modal open states                                                                                 | 390, 1440 | 14       |
| dropdown open (transactions quick-dates)                                                            | 1440      | 1        |
| 4 toast variants (success, error, info, warning)                                                    | 1440      | 4        |
| loading skeleton, empty table, error state                                                          | 1440      | 3        |

41 captures (the table's 40 plus a `state-spinner` desktop capture added during Phase 0, which gave the migrated `loading-spinner.vue` a baseline it would otherwise not have had), committed under `docs/superpowers/baseline/` as `<surface>-<state>-<viewport>.png`, with an `index.md` recording how each was produced (route, viewport, interaction sequence) so any phase can reproduce it exactly.

### Per phase

1. Migrate the phase's components.
2. Re-capture the affected surfaces using the recorded interaction sequence.
3. Diff against baseline.
4. Run `impeccable audit` on the changed files.
5. `npm run lint-format` and `npm test -- --run`.
6. Commit, open PR with the diff result in the description.

Phase 10 inverts step 3: diffs are expected and reviewed by eye.

**What "diff = 0" means.** Compared with ImageMagick `compare -metric AE -fuzz 2%`. Zero differing pixels at 2% per-channel fuzz, which absorbs antialiasing and subpixel text rendering without absorbing a real color, spacing, or weight change. A 1px shift in any edge exceeds it. Regions listed as masked in the baseline index (live prices, chart canvases) are excluded via `-region`. Anything above zero in Phases 1–9 is a regression and gets fixed, not accepted.

### Known verification caveats

- `npm run lint-format` already exits non-zero on this machine, from knip findings in an unrelated `worktrees/` checkout outside this repo. Pre-existing; the gate is "no _new_ findings", not "exit 0".
- `eslint ui --fix` strips the `/* eslint-disable */` header from `ui/models/generated/domain-models.ts` on every run. Revert that file rather than committing it. Excluding the generated directory from eslint is out of scope here.
- 10 test files are coupled to Bootstrap and are updated in the phase that touches their subject, not in a separate test pass:

  | Phase | Test files                                                                     |
  | ----- | ------------------------------------------------------------------------------ |
  | 1     | `data-table`                                                                   |
  | 3     | `allocation-table`, `etf-breakdown-table`, `instrument-table`, `style-classes` |
  | 5     | `confirm-dialog`, `config-dialog`, `instrument-modal`, `use-bootstrap-modal`   |
  | 7     | `instruments-view`                                                             |

  `style-classes.test.ts` is deleted with the module it tests, not rewritten.

## Phase plan

Each phase is one PR off `feature/1662-tailwind-v4-migration`.

| #   | Phase             | Scope                                                                                                                                                 | Exit criteria                                                 |
| --- | ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| 0   | Baseline          | Capture 40 references from `main`                                                                                                                     | Committed with reproducible index                             |
| 1   | Shared primitives | `loading-spinner`, `skeleton-loader`, `form-input`, `crud-layout`, `data-table`                                                                       | diff = 0; `data-table` test updated                           |
| 2   | Platform filter   | Extract duplicated markup from 5 views into one shared component, migrate it                                                                          | diff = 0; 5 views reference the shared component              |
| 3   | Tables            | `transaction-table`, `instrument-table`, `etf-breakdown-table`, `allocation-table`; delete `style-classes.ts`; remove the `v-html`                    | diff = 0; 4 tests handled; no `v-html` remains                |
| 4   | Cards + charts    | `allocation-card`, `breakdown-card`, `diversification-stats`, `portfolio-chart`, `etf-breakdown-chart`, `bar-chart`, `line-chart`                     | diff = 0                                                      |
| 5   | Modals            | Extract one `<dialog>` shell with `v-model:open`; migrate 6 modals; delete `use-bootstrap-modal`                                                      | diff = 0; 4 tests handled; `Modal` import gone                |
| 6   | Toast + dropdown  | Toast → Vue component with `<Teleport>`; dropdown → Popover API                                                                                       | diff = 0; `Toast`/`Dropdown` imports gone; XSS closed         |
| 7   | Views + shell     | `nav-bar`, `app`, 6 route components, 2 forms, `etf-breakdown-header`, `portfolio-actions`                                                            | diff = 0; 1 test updated                                      |
| 8   | Global SCSS sweep | Delete now-dead app-level SCSS; migrate any surviving rules into `theme.css`                                                                          | diff = 0; the only `.scss` left is Bootstrap's own entry pair |
| 9   | Removal           | Drop `framework/bootstrap.scss`, `_bootstrap-overrides.scss`; enable preflight; uninstall `bootstrap`, `@types/bootstrap`, `sass`; strip `tw:` prefix | diff = 0; no `bootstrap` in `package.json`; no `.scss` files  |
| 10  | Defect fixes      | DESIGN.md defects 1–3 (tabular-nums, palette drift, font stack)                                                                                       | diffs expected and reviewed                                   |

### Coverage check

All 36 components are accounted for. Components already clean still get a phase slot, because their surrounding markup and the global CSS they inherit from still change.

| Phase | n   | Components                                                                                                                                                                                                                      |
| ----- | --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| done  | 2   | `currency-flag`, `currency-split-card`                                                                                                                                                                                          |
| 1     | 5   | `crud-layout`, `data-table`, `form-input`, `loading-spinner`, `skeleton-loader`                                                                                                                                                 |
| 3     | 4   | `allocation-table`, `etf-breakdown-table`, `instrument-table`, `transaction-table`                                                                                                                                              |
| 4     | 7   | `allocation-card`, `breakdown-card`, `diversification-stats`, `portfolio-chart`, `etf-breakdown-chart`, `bar-chart`, `line-chart`                                                                                               |
| 5     | 6   | `confirm-dialog`, `config-dialog`, `instrument-modal`, `logo-replacement-modal`, `xirr-windows-modal`, `annual-windows-modal`                                                                                                   |
| 7     | 12  | `app`, `nav-bar`, `portfolio-summary`, `transactions-view`, `instruments-view`, `etf-breakdown`, `diversification-calculator`, `calculator`, `etf-breakdown-header`, `portfolio-actions`, `instrument-form`, `transaction-form` |

Phase 2 adds one new shared component rather than migrating an existing one. Phases 6, 8, 9, and 10 change composables, styles, and config rather than component inventory — except Phase 6, which adds a toast component and deletes `use-toast.ts`'s DOM building.

### Phase 5 detail: the `<dialog>` shell

The 6 modals are opened two different ways today, and both collapse into one shell:

- **String-ID lookup.** `use-bootstrap-modal.ts` is a 50-line wrapper that calls `document.getElementById(id)` and wraps `new Modal(el)`. `instruments-view.vue` drives 3 modals this way (`instrumentModal`, `xirrWindowsModal`, `annualWindowsModal`), and `instrument-modal.vue` carries a `modalId` prop purely to feed it.
- **Direct construction.** `confirm-dialog`, `logo-replacement-modal`, and `config-dialog` each build `new Modal(templateRef)` themselves.

The replacement is a single `<dialog>`-based shell component controlled by `v-model:open`. That removes the `getElementById` indirection and the `modalId` prop — the parent owns a boolean instead of a string that has to match an element ID somewhere else in the tree. Mechanically:

- `show()` → `dialogEl.showModal()`
- `hide()` → `dialogEl.close()`
- `isVisible` → the native `close` event plus `dialogEl.open`
- focus trap, Esc-to-close, backdrop, and top-layer stacking come from the browser
- `::backdrop` replaces `.modal-backdrop`
- the 6 `data-bs-dismiss` attributes become `@click="close"`

`use-bootstrap-modal.ts` and its test are deleted rather than ported; `v-model:open` on the shell is the whole API.

### Phase 6 detail: toast

Today `use-toast.ts` builds DOM by hand and interpolates `message` into `innerHTML`, which is an XSS vector for any server- or user-derived text. The replacement is a Vue component with a reactive array and `<Teleport to="body">`; Vue escapes interpolated text, closing the hole as a side effect of the migration. The public API (`success`/`error`/`info`/`warning` with per-type durations of 4000/7500/5000/6000ms) is preserved so no call site changes.

### Phase 10 detail: which defects

DESIGN.md states its defects inline in the Do's and Don'ts rather than as a list. Extracted and verified on this branch:

| #   | Defect                                                                                         | Verified                                     | Fix                                                    |
| --- | ---------------------------------------------------------------------------------------------- | -------------------------------------------- | ------------------------------------------------------ |
| 1   | `font-variant-numeric: tabular-nums` missing entirely, so money columns shift as digits change | 0 occurrences in `ui/`                       | Add to numeric table and card cells                    |
| 2   | Three-way palette drift: `#22c55e` alongside `#21c55d`, `#dc2626` alongside `#dc3545`          | 14 occurrences of the 4 near-duplicate hexes | Collapse onto the `gain`/`loss` tokens                 |
| 3   | Two disagreeing font stacks; `#app`'s Avenir overrides the token layer's system stack          | `app.scss:9`                                 | Single `--font-sans`, already declared in `theme.css`  |
| 4   | `var(--text-muted)` referenced but never defined, silently resolving to nothing                | `_mixins.scss:45` and `:57`                  | Point the scrollbar mixin at `--color-ink-muted`       |
| 5   | Dormant `[data-bs-theme='dark']` block presented as if it were dark mode                       | `_modern-enhancements.scss:40`               | Resolved by deletion in Phase 9, not fixed in Phase 10 |

Defect 4 is fixed earlier if Phase 8 deletes `_mixins.scss` outright; in that case it is resolved by deletion too. Only defects 1–3 are guaranteed Phase 10 work, and only they are expected to produce screenshot diffs.

## Risks

| Risk                                                                                  | Mitigation                                                                                                                                                                                                                                     |
| ------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Screenshot diffs are noisy from live market data changing between captures            | Capture baseline and comparisons against the same data. Where a chart or price cell is inherently volatile, mask that region in the diff and note it in the index.                                                                             |
| Phase 9's preflight causes a large one-time diff                                      | Expected and scoped to one phase. Heading margins, list styles, and form element font inheritance differ between reboot and preflight. Reviewed by eye; fall back to a minimal hand-written reset if churn is unreasonable.                    |
| Native `<dialog>` behaves differently from Bootstrap Modal in ways tests do not catch | Phase 5 is its own PR. The 3 component modal tests are rewritten against `v-model:open` rather than patched, `use-bootstrap-modal.test.ts` is replaced by a test on the new shell, and the 6 modal open states are in the screenshot baseline. |
| A phase's diff is non-zero but the cause is genuinely cosmetic-equivalent             | Not accepted silently. Either fix it or record it explicitly in the PR as accepted drift with rationale.                                                                                                                                       |
| Scope creep from "improving" adjacent code                                            | Phases 2 and 5 are the only sanctioned extractions, and both are prerequisites. Everything else is a mechanical translation.                                                                                                                   |

## Success criteria

- `package.json` contains no `bootstrap`, `@types/bootstrap`, or `sass`.
- No `.scss` files remain under `ui/`.
- No `data-bs-*` attributes remain.
- No `from 'bootstrap'` imports remain.
- All tests pass, with the 10 Bootstrap-coupled test files updated to assert on the new markup (or deleted, for `style-classes`). Test count drops slightly as `style-classes.test.ts` goes.
- Final screenshots differ from baseline only by the Phase 10 defect fixes.
- No `tw:` prefix remains; utilities are unprefixed.
