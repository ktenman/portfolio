# Bootstrap CSS Retirement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire the Bootstrap-ported CSS layer from `ui/styles/`, delete dead CSS, fix the E2E retry bug that hid it, and land a small curated 2026 polish pass — without making anything look worse.

**Architecture:** Five phases, each one GitHub issue + `feature/<issue>-<slug>` branch + PR. Phases A–D are zero-visual-delta and prove it by running the existing Playwright pixel gate green against the committed baselines. Phase E is the only phase that changes pixels; it re-baselines inside a single PR, one polish change per commit so each visual diff is attributable.

**Tech Stack:** Vue 3.5 + TypeScript 6 + Vite 8, Tailwind CSS 4.3 (CSS-first `@theme` config), Vitest, Playwright (visual gate, runs in Docker), Kotlin/JUnit 5 + Selenide (E2E), Gradle.

## Global Constraints

- **No code comments.** Not `//`, not `/* */`, not `/** */`. TypeScript triple-slash directives are the only exception. This applies to CSS too.
- **Commit subjects:** uppercase imperative verb, max 50 chars, no `feat:`/`fix:`/`chore:` prefixes.
- **No AI attribution** in commits, PR bodies, or code. No "Generated with", no "Co-Authored-By: Claude".
- **Never commit to `main`.** Every phase branches from `main` as `feature/<issue-number>-<slug>`, PR references `Closes #XXX`.
- **Class names are DOM hooks.** `.btn`, `.btn-close`, `.alert`, `.alert-danger`, `.alert-info`, `.modal-*`, `.platform-btn` are selected by Selenide E2E, Vitest unit tests, and `ui/tests/visual/states.spec.ts`. Never rename or drop one from rendered markup.
- **Visual gate command:** `npm run visual` (Playwright in Docker). Update baselines only with `npm run visual:update`, and only in Phase E.
- **Frontend gate after any UI change:** `npm run lint-format` and `npm test -- --run`, both green.
- **`npm run lint-format` mutates `ui/models/generated/domain-models.ts`** (strips its eslint-disable). That diff is churn — revert it, never commit it.
- **Preserve exact computed values** in Phases A–D. A swap is legal only when old and new resolve to the identical value.
- Scoped `<style>` in a Vue SFC is injected **unlayered** and outranks every `@layer` rule. Relocating a rule into a component raises its precedence.
- **`Closes #XXX` in every PR body is a placeholder.** Create the phase's GitHub issue first and substitute the real number.
- **Out of scope entirely:** `ui/styles/components/controls.css` and `motion.css` styling, the skeleton rules, and the `base-select` block in `forms.css`. These are already Statement-native. Token references inside them are swapped in Phase C, but no rule is otherwise restyled.
- **`autofocus` on `.modal-content` in `modal-shell.vue` is load-bearing.** The visual harness calls `showModal()` raw, so a JS `dialog.focus()` never fires and the focus ring lands on `.btn-close` instead — a 33-pixel diff. Never remove that attribute or replace it with scripted focus.

---

## File Structure

**Deleted outright:**

- `ui/styles/components/navigation.css` — `.dropdown`, `.dropdown-menu`, `.dropdown-menu.show`, `.dropdown-item` (lines 25-27, 45-83); file survives with `.navbar`, `.navbar-nav`, `.nav-link`, `.dropdown-toggle::after` until Phase B empties it.
- `ui/styles/components/feedback.css` — `.toast:not(.show)` (lines 112-114).

**Relocated (Phase B):**

- `ui/styles/components/modals.css` (85 lines) → `ui/components/shared/modal-shell.vue` scoped style, then file deleted.
- `.navbar`/`.navbar-nav`/`.nav-link` → `ui/components/nav-bar.vue`.
- `.dropdown-toggle::after` + `[aria-expanded='true']` → `ui/components/shared/filter-toggle.vue`.
- `.toast*` block → `ui/components/shared/toast-container.vue`.
- Global `:focus-visible` rule (`buttons.css:29-36`) → `ui/styles/base.css`.
- `ui/styles/components/navigation.css` deleted once empty; `ui/styles/components.css` barrel updated.

**Created (Phase D):**

- `ui/components/shared/app-button.vue` — one button primitive. Props `variant`, `size`, `ghost`, `loading`, `disabled`, `type`. Renders existing Bootstrap class names.
- `ui/components/shared/alert-message.vue` — one alert primitive. Props `variant`, `dismissible`. Renders `.alert .alert-<variant>` + `.btn-close`.

**Modified (Phase C):** `ui/styles/theme.css` (token block), plus every file in the token census below.

**Modified (Phase E):** `ui/styles/components/feedback.css` (spinner, badge), `ui/styles/components/surfaces.css` (borders, label gesture), `ui/styles/components/mobile-cards.css`, and the 7 component files carrying label-gesture variants.

**Backend (Phase A):**

- `src/test/kotlin/e2e/retry/RetryExtension.kt` — rewritten.
- `src/test/kotlin/ee/tenman/portfolio/testing/RetryExtensionTest.kt` — created (outside `**/e2e/**` so it runs in the fast `./gradlew test` task).
- `src/test/kotlin/e2e/TransactionManagementE2E.kt` — `should display quick dates dropdown` rewritten.

---

## Phase A — Fix the retry bug, then the CSS it was hiding

Branch: `fix/<issue>-e2e-retry-and-dead-css`. Zero visual delta.

### Task 1: Make `RetryExtension` actually retry

`RetryExtension` implements `TestExecutionExceptionHandler`. When it decides to "retry", it `return`s — and a `TestExecutionExceptionHandler` that returns normally tells JUnit **the test passed**. Nothing is re-run. All 4 E2E classes (29 tests) opt in with `onExceptions = [ElementNotFound::class, TimeoutException::class]`, so every missing-element failure in the suite has been silently green.

**Files:**

- Modify: `src/test/kotlin/e2e/retry/RetryExtension.kt` (full rewrite)
- Test: `src/test/kotlin/ee/tenman/portfolio/testing/RetryExtensionTest.kt` (create)

**Interfaces:**

- Consumes: `e2e.retry.Retry` annotation (`times: Int = 3`, `onExceptions: Array<KClass<out Throwable>> = [Exception::class]`) — unchanged.
- Produces: `e2e.retry.RetryExtension` implementing `org.junit.jupiter.api.extension.InvocationInterceptor`. `times` means **total attempts**, not extra attempts (documented change from the old handler's "up to N extra runs"). Values below 1 fail fast.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/ee/tenman/portfolio/testing/RetryExtensionTest.kt`:

```kotlin
package ee.tenman.portfolio.testing

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import e2e.retry.Retry
import e2e.retry.RetryExtension
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RetryExtension::class)
@Retry(times = 3, onExceptions = [IllegalStateException::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetryExtensionTest {
  private var attempts = 0

  @Test
  fun `should re-invoke the test method until an attempt succeeds`() {
    attempts++
    if (attempts < 3) throw IllegalStateException("attempt $attempts fails on purpose")
    expect(attempts).toEqual(3)
  }

  @AfterAll
  fun `should have re-invoked the method three times`() {
    expect(attempts).toEqual(3)
  }
}
```

The `@AfterAll` hook is what makes this test able to go red. The broken extension reports a swallowed failure as a **pass**, so an assertion inside the test method can never fail — the method simply stops executing at the throw. `@AfterAll` is not intercepted, runs regardless, and sees `attempts == 1`, failing the class.

`@TestInstance(PER_CLASS)` is required so `@AfterAll` can be an instance method sharing the counter. The class holds exactly one test method, so nothing is shared between tests.

- [ ] **Step 2: Run it and watch it fail**

```bash
./gradlew test --tests "ee.tenman.portfolio.testing.RetryExtensionTest"
```

Expected: FAIL, from `@AfterAll` — `expected: 3, actual: 1`. The test method itself is reported green, which is precisely the bug: it threw on attempt 1, the handler swallowed it, and no re-invocation happened.

- [ ] **Step 3: Rewrite the extension**

Replace the entire contents of `src/test/kotlin/e2e/retry/RetryExtension.kt`:

```kotlin
package e2e.retry

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class RetryExtension : InvocationInterceptor {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    val retry = resolve(invocationContext.executable, extensionContext)
    if (retry == null) {
      invocation.proceed()
      return
    }
    invocation.skip()
    attempt(retry, invocationContext, extensionContext)
  }

  private fun resolve(
    method: Method,
    context: ExtensionContext,
  ): Retry? =
    method.getAnnotation(Retry::class.java)
      ?: context.requiredTestClass.getAnnotation(Retry::class.java)

  private fun attempt(
    retry: Retry,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    require(retry.times >= 1) { "Retry times must be at least 1 but was ${retry.times}" }
    val method = invocationContext.executable
    val target = invocationContext.target.orElseThrow()
    val arguments = invocationContext.arguments.toTypedArray()
    val name = "${extensionContext.requiredTestClass.simpleName}.${method.name}"
    var lastFailure: Throwable? = null
    repeat(retry.times) { index ->
      val outcome = runCatching { method.invoke(target, *arguments) }
      if (outcome.isSuccess) return
      val failure = outcome.exceptionOrNull().unwrap()
      if (!retry.matches(failure)) throw failure
      lastFailure = failure
      log.info(
        "Test {} failed attempt {} of {}: {}",
        name,
        index + 1,
        retry.times,
        failure.message,
      )
    }
    throw lastFailure ?: IllegalStateException("Retry produced no outcome for $name")
  }
}

private fun Throwable?.unwrap(): Throwable {
  val throwable = checkNotNull(this) { "Cannot unwrap a null failure" }
  return (throwable as? InvocationTargetException)?.targetException ?: throwable
}

private fun Retry.matches(throwable: Throwable): Boolean =
  onExceptions.isEmpty() || onExceptions.any { it.isInstance(throwable) }
```

Notes for the implementer:

- JUnit requires exactly one of `proceed()` / `skip()` per invocation. `skip()` hands control back so the method can be invoked reflectively in a loop.
- `method.invoke` wraps test failures in `InvocationTargetException`; `unwrap()` restores the real cause so `onExceptions` matching and the final rethrow see the original exception.
- `@BeforeEach` / `@AfterEach` run **once** around the whole retry loop, not per attempt. For the Selenide E2E classes that means the page is opened once and retries re-query the DOM — acceptable, and strictly better than today's zero re-runs. Do not try to re-run lifecycle callbacks.
- Top-level private extension functions live in the same file, which is allowed: this is a test class, not a Spring stereotype or `@Entity`.

- [ ] **Step 4: Run the test and verify it passes**

```bash
./gradlew test --tests "ee.tenman.portfolio.testing.RetryExtensionTest"
```

Expected: PASS — both the test method and `@AfterAll`. The log shows two lines, `Test RetryExtensionTest.should re-invoke... failed attempt 1 of 3` and `attempt 2 of 3`, before the third attempt succeeds.

- [ ] **Step 5: Verify a non-matching exception is not retried**

Temporarily change the test's thrown type to `IllegalArgumentException` (not in `onExceptions`), run, and confirm the test fails immediately with exactly one attempt logged. Then revert the change. Do not commit this variant.

- [ ] **Step 6: Verify the whole fast suite still passes**

```bash
./gradlew test
```

Expected: PASS. `**/e2e/**` is excluded from this task, so only the new test exercises the extension here.

- [ ] **Step 7: Commit**

```bash
git add src/test/kotlin/e2e/retry/RetryExtension.kt src/test/kotlin/ee/tenman/portfolio/testing/RetryExtensionTest.kt
git commit -m "Make the E2E retry extension actually retry"
```

### Task 2: Triage the E2E suite now that failures surface

**Files:**

- Modify: `src/test/kotlin/e2e/TransactionManagementE2E.kt:69-81`
- Possibly modify: other files under `src/test/kotlin/e2e/` depending on what the run reveals

**Interfaces:**

- Consumes: the working `RetryExtension` from Task 1.
- Produces: a green E2E suite, or an explicit written list of failures deferred with reasons.

- [ ] **Step 1: Run the full E2E suite and capture the truth**

```bash
npm run test:e2e
```

This starts Docker, the backend on 8081 and Vite on 61234, then runs `E2E=true ./gradlew test`. Expect at minimum `should display quick dates dropdown` to fail with `ElementNotFound` on `.dropdown-menu` — that failure is the whole point of Task 1. Record every failing test name before changing anything.

- [ ] **Step 2: Rewrite the quick-dates test against the real control**

The UI replaced the popup menu with a native `<select>` (`ui/components/transactions/transactions-view.vue:43-56`). Replace the method at `src/test/kotlin/e2e/TransactionManagementE2E.kt:69-81`:

```kotlin
  @Test
  fun `should filter transactions using a quick date range`() {
    val quickDates = element(cssSelector("[data-testid='quickDatesToggle']"))
    quickDates.shouldBe(visible, Duration.ofSeconds(10))
    expect(quickDates.findAll(tagName("option")).size()).toBeGreaterThan(1)

    quickDates.selectOptionContainingText("Last")
    element(id("fromDate")).shouldNotBe(empty)
  }
```

Add the imports this needs: `com.codeborne.selenide.Condition.empty` and keep the existing `id`, `cssSelector`, `tagName` imports. Remove the now-unused `className` import only if no other test in the file uses it — check with `grep -n "className(" src/test/kotlin/e2e/TransactionManagementE2E.kt` first.

If `selectOptionContainingText("Last")` finds no match, list the real option labels from `ui/composables/use-quick-dates.ts` (`QUICK_DATE_OPTIONS`) and select an exact label instead. Do not invent labels.

- [ ] **Step 3: Re-run E2E and confirm the rewritten test passes**

```bash
npm run test:e2e
```

Expected: `should filter transactions using a quick date range` PASSES.

- [ ] **Step 4: Fix or report the remaining failures**

For each other test that Step 1 revealed:

- If the fix is a selector or assertion drift of the same kind (the UI moved, the test didn't), fix it in place.
- If a failure indicates a genuine app bug, stop and report it rather than editing the test to match broken behaviour.
- If the suite reveals more rot than this phase can absorb, list the remaining failures explicitly in the PR body. Do not silence anything by re-adding a swallow.

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/e2e/
git commit -m "Assert the real quick date control in E2E"
```

### Task 3: Delete the dead CSS the broken test was protecting

Grep proves zero consumers in any template or runtime class binding. `.dropdown-toggle::after` **is** live (`filter-toggle.vue`) and must survive.

**Files:**

- Modify: `ui/styles/components/navigation.css:25-27,45-83`
- Modify: `ui/styles/components/feedback.css:112-114`

**Interfaces:**

- Consumes: nothing.
- Produces: `navigation.css` reduced to `.navbar`, `.navbar-nav`, `.nav-link`, `.dropdown-toggle::after`, `.dropdown-toggle[aria-expanded='true']::after`.

- [ ] **Step 1: Re-verify the classes are dead before deleting**

```bash
grep -rn "dropdown-menu\|dropdown-item\|\bdropdown\b" ui --exclude-dir=node_modules
grep -rn "dropdown" src/test/kotlin/e2e/
```

Expected: only `.dropdown-toggle` hits in `ui/components/shared/filter-toggle.vue` and the rules in `navigation.css`. Zero hits for `dropdown-menu` / `dropdown-item` outside `navigation.css`. Zero hits in E2E after Task 2. If anything else appears, stop and re-scope.

- [ ] **Step 2: Delete `.dropdown` from `navigation.css`**

Remove lines 25-27:

```css
.dropdown {
  position: relative;
}
```

- [ ] **Step 3: Delete `.dropdown-menu`, `.dropdown-menu.show`, `.dropdown-item` from `navigation.css`**

Remove lines 45-83 — the `.dropdown-menu` block, the `.dropdown-menu.show` block, the `.dropdown-item` block, and the `.dropdown-item:hover, .dropdown-item:focus` block. Keep `.dropdown-toggle::after` (lines 29-39) and `.dropdown-toggle[aria-expanded='true']::after` (lines 41-43) exactly as they are.

- [ ] **Step 4: Delete `.toast:not(.show)` from `feedback.css`**

`ui/components/shared/toast-container.vue:7` renders `class="toast show"` unconditionally, so this rule can never match. Remove lines 112-114:

```css
.toast:not(.show) {
  display: none;
}
```

- [ ] **Step 5: Verify no visual change**

```bash
npm run lint-format
npm test -- --run
npm run visual
```

Expected: all three green. `npm run visual` green means zero differing pixels against the committed baselines — the deletion changed nothing on screen. Revert any `ui/models/generated/domain-models.ts` churn from `lint-format` before committing.

- [ ] **Step 6: Commit and open the PR**

```bash
git add ui/styles/components/navigation.css ui/styles/components/feedback.css
git commit -m "Delete unreachable dropdown and toast CSS"
gh pr create --title "Fix E2E retry and delete dead CSS" --body "$(cat <<'EOF'
## Summary

- Rewrite `RetryExtension` as an `InvocationInterceptor` so failing E2E tests are actually re-run instead of being reported as passed
- Assert the real native `<select>` quick-date control instead of a dropdown menu the UI no longer renders
- Delete `.dropdown`, `.dropdown-menu`, `.dropdown-item` and `.toast:not(.show)` — zero consumers

## Test plan

- [ ] `./gradlew test` green, including the new `RetryExtensionTest`
- [ ] `npm run test:e2e` green
- [ ] `npm run visual` green against unchanged baselines
- [ ] `npm run lint-format` and `npm test -- --run` green

Closes #XXX
EOF
)"
```

---

## Phase B — Relocate single-consumer CSS into its component

Branch: `feature/<issue>-relocate-component-css`. Zero visual delta. Each moved block styles only elements inside its own component's template, so the precedence jump from `@layer components` to unlayered scoped CSS changes nothing.

### Task 4: Move the modal CSS into `modal-shell.vue`

**Files:**

- Delete: `ui/styles/components/modals.css` (85 lines)
- Modify: `ui/components/shared/modal-shell.vue` (scoped `<style>`)
- Modify: `ui/styles/components.css` (drop the import)

**Interfaces:**

- Consumes: nothing.
- Produces: `.modal`, `.modal-dialog`, `.modal-dialog-centered`, `.modal-content`, `.modal-header`, `.modal-title`, `.modal-body`, `.modal-footer`, `.modal-lg` defined inside `modal-shell.vue`.

- [ ] **Step 1: Confirm `modal-shell.vue` is the only consumer**

```bash
grep -rn "modal-dialog\|modal-content\|modal-header\|modal-body\|modal-footer\|modal-title\|modal-lg" ui --exclude-dir=node_modules | grep -v "ui/styles/components/modals.css"
```

Expected: hits only in `ui/components/shared/modal-shell.vue` and in `ui/tests/visual/states.spec.ts` / `ui/components/shared/modal-shell.test.ts` as selectors. If any other component renders these classes directly, stop — the CSS must stay global.

- [ ] **Step 2: Move the rules**

Append the entire contents of `ui/styles/components/modals.css` (all 85 lines, unchanged, including both media queries) into the existing scoped `<style>` block of `ui/components/shared/modal-shell.vue`. Leave the `autofocus` attribute on `.modal-content` exactly where it is — see Global Constraints. Keep `deep()` out of it — these classes are on elements in this component's own template, so scoping attributes apply directly. Do not reformat, reorder, or "improve" any declaration.

Because Vue scoped styles add an attribute selector, verify the classes are all rendered by `modal-shell.vue` itself and not by slot content passed from a parent. Slotted content does **not** receive this component's scope attribute. If any `.modal-*` class appears only inside a `<slot>` consumer, that rule needs `:deep()`.

- [ ] **Step 3: Delete the file and its import**

```bash
rm ui/styles/components/modals.css
```

Remove the `@import './components/modals.css';` line from `ui/styles/components.css`.

- [ ] **Step 4: Verify no visual change**

```bash
npm run lint-format
npm test -- --run
npm run visual
```

Expected: all green. The visual suite includes 7 modal screenshots — this is the load-bearing check for this task. If a modal shot regresses, the most likely cause is slotted content losing scope: convert the affected rule to `:deep(.modal-x)` and re-run.

- [ ] **Step 5: Commit**

```bash
git add ui/components/shared/modal-shell.vue ui/styles/components.css
git rm ui/styles/components/modals.css
git commit -m "Move modal styles into modal-shell"
```

### Task 5: Move navbar and caret CSS into their components

**Files:**

- Modify: `ui/components/nav-bar.vue` (scoped `<style>`)
- Modify: `ui/components/shared/filter-toggle.vue` (scoped `<style>`)
- Delete: `ui/styles/components/navigation.css`
- Modify: `ui/styles/components.css`

**Interfaces:**

- Consumes: `navigation.css` as reduced by Task 3.
- Produces: `.navbar`, `.navbar-nav`, `.nav-link` in `nav-bar.vue`; `.dropdown-toggle::after` and `.dropdown-toggle[aria-expanded='true']::after` in `filter-toggle.vue`.

- [ ] **Step 1: Confirm single consumers**

```bash
grep -rn "navbar\|nav-link" ui --exclude-dir=node_modules | grep -v "ui/styles/components/navigation.css"
grep -rn "dropdown-toggle" ui --exclude-dir=node_modules | grep -v "ui/styles/components/navigation.css"
```

Expected: navbar classes only in `ui/components/nav-bar.vue` (plus any `nav a` selector in E2E, which is a tag selector and unaffected); `dropdown-toggle` only in `ui/components/shared/filter-toggle.vue`.

- [ ] **Step 2: Move the navbar rules into `nav-bar.vue`**

Move these three blocks verbatim from `navigation.css` into the scoped `<style>` of `ui/components/nav-bar.vue`:

```css
.navbar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0;
}

.navbar-nav {
  display: flex;
  margin-bottom: 0;
  padding-left: 0;
  list-style: none;
}

.nav-link {
  display: block;
  padding: 0.5rem;
  font-weight: 500;
  color: rgb(0 0 0 / 0.65);
  text-decoration: none;
  transition: color var(--transition-fast);
}
```

Leave the `rgb(0 0 0 / 0.65)` value exactly as-is — Phase C decides its fate, not this task.

- [ ] **Step 3: Move the caret rules into `filter-toggle.vue`**

Move these two blocks verbatim into the scoped `<style>` of `ui/components/shared/filter-toggle.vue`:

```css
.dropdown-toggle::after {
  display: inline-block;
  margin-left: 0.255em;
  vertical-align: 0.255em;
  content: '';
  border-top: 0.3em solid;
  border-right: 0.3em solid transparent;
  border-bottom: 0;
  border-left: 0.3em solid transparent;
  transition: transform var(--transition-fast);
}

.dropdown-toggle[aria-expanded='true']::after {
  transform: rotate(180deg);
}
```

- [ ] **Step 4: Delete the now-empty file and its import**

```bash
rm ui/styles/components/navigation.css
```

Remove `@import './components/navigation.css';` from `ui/styles/components.css`.

- [ ] **Step 5: Verify no visual change**

```bash
npm run lint-format
npm test -- --run
npm run visual
```

Expected: all green. The navbar appears in every route screenshot, so any regression shows up across all 3 viewports immediately.

- [ ] **Step 6: Commit**

```bash
git add ui/components/nav-bar.vue ui/components/shared/filter-toggle.vue ui/styles/components.css
git rm ui/styles/components/navigation.css
git commit -m "Move navbar and caret styles into components"
```

### Task 6: Move toast CSS into `toast-container.vue` and the focus ring into `base.css`

The `:focus-visible` rule sitting in `buttons.css:29-36` is global — it targets `a[href]`, `input`, `select`, `textarea`, none of which are buttons. It belongs in `base.css`. `base.css` is imported into `@layer base`, and `buttons.css` into `@layer components`; utilities and components both outrank base. Verify no `@layer components` or utility rule sets `outline` on these elements before moving.

**Files:**

- Modify: `ui/components/shared/toast-container.vue` (scoped `<style>`)
- Modify: `ui/styles/components/feedback.css` (remove the toast block)
- Modify: `ui/styles/components/buttons.css:29-36` (remove)
- Modify: `ui/styles/base.css` (add the focus rule)

**Interfaces:**

- Consumes: `feedback.css` as reduced by Task 3.
- Produces: `.toast-container`, `.toast`, `.toast .btn-close`, `.toast-container > :not(:last-child)`, `.toast-success/-info/-error/-warning`, `.toast-body` inside `toast-container.vue`. `feedback.css` keeps `.alert*`, `.small`, `.badge`, `.spinner-border*`.

- [ ] **Step 1: Confirm the toast classes have one consumer**

```bash
grep -rn "toast" ui --exclude-dir=node_modules | grep -v "ui/styles/components/feedback.css" | grep -v "\.test\.ts"
```

Expected: only `ui/components/shared/toast-container.vue` and the `use-toast` composable that feeds it (which deals in data, not classes).

- [ ] **Step 2: Move the toast block**

Move `feedback.css` lines 88-143 — `.toast-container`, `.toast`, `.toast .btn-close`, `.toast-container > :not(:last-child)`, the four `.toast-*` variants, `.toast-body` — verbatim into the scoped `<style>` of `ui/components/shared/toast-container.vue`. Keep `--toast-bg` and the `var(--color-control-graphite)` / `var(--color-status-*)` references untouched; Phase C rewrites them.

`toast-container.vue` uses a `<Teleport to="body">`. Teleported content **keeps** the scope attribute of the component that declares it, so scoped styles still apply. Confirm this holds by checking the rendered toast in the visual gate rather than by reasoning alone.

- [ ] **Step 3: Move the focus-visible rule to `base.css`**

Cut lines 29-36 from `ui/styles/components/buttons.css`:

```css
a[href]:focus-visible,
button:focus-visible,
input:focus-within,
select:focus-visible,
textarea:focus-visible {
  outline: 2px solid var(--color-brass);
  outline-offset: 2px;
}
```

Paste it into `ui/styles/base.css` immediately after the `label` rule (currently lines 87-89), before the `hr` rule.

- [ ] **Step 4: Verify the focus ring still wins**

```bash
grep -rn "outline" ui/styles ui/components --include=*.css --include=*.vue | grep -v "outline: 0" | grep -v "outline-offset"
```

Any rule in `@layer components`, in a scoped style, or in a Tailwind utility that sets `outline` on a focusable element now outranks the moved base rule. `modals.css`/`modal-shell.vue` sets `outline: 0` on `.modal-content` — that element is not in the moved selector list, so it is unaffected. If any other conflict appears, keep the rule in `@layer components` instead and note why in the PR.

- [ ] **Step 5: Verify no visual change**

```bash
npm run lint-format
npm test -- --run
npm run visual
```

Expected: all green. `ui/tests/visual/palette.spec.ts` walks focus rings — it is the gate for Step 3. The `states.spec.ts` toast shots are the gate for Step 2.

- [ ] **Step 6: Refresh the DESIGN.md citations this phase invalidated**

`modals.css` and `navigation.css` no longer exist, and the toast and focus-ring line numbers have moved. Grep `DESIGN.md` for every citation into those four files and re-resolve each to its new home (`modal-shell.vue`, `nav-bar.vue`, `filter-toggle.vue`, `toast-container.vue`, `base.css`):

```bash
grep -n "modals.css\|navigation.css\|feedback.css\|buttons.css" DESIGN.md
```

- [ ] **Step 7: Commit and open the PR**

```bash
git add ui/components/shared/toast-container.vue ui/styles/components/feedback.css ui/styles/components/buttons.css ui/styles/base.css DESIGN.md
git commit -m "Move toast styles and the focus ring to their homes"
gh pr create --title "Relocate single-consumer CSS into components" --body "$(cat <<'EOF'
## Summary

- Move modal, navbar, caret and toast CSS into the single component that renders each
- Move the global `:focus-visible` rule out of `buttons.css` into `base.css`
- Delete `ui/styles/components/modals.css` and `ui/styles/components/navigation.css`
- Refresh the DESIGN.md citations pointing into the deleted files

No rule text changed — only where each rule lives. The pixel gate is unchanged and green.

## Test plan

- [ ] `npm run visual` green against unchanged baselines, including all 7 modal shots and the toast states
- [ ] `npm run lint-format` and `npm test -- --run` green
- [ ] Every DESIGN.md citation into `modals.css` / `navigation.css` re-resolved

Closes #XXX
EOF
)"
```

---

## Phase D — Extract the two shared primitives

Branch: `feature/<issue>-button-and-alert-components`. Zero visual delta. Both components render the **existing** class names, so every Selenide, Vitest and Playwright selector keeps working untouched.

### Task 7: Extract `app-button.vue`

19 files render `.btn`. The markup repeats `:disabled`, the `.btn-spinner` span, and variant class juggling.

**Files:**

- Create: `ui/components/shared/app-button.vue`
- Create: `ui/components/shared/app-button.test.ts`
- Modify: the 19 files listed in Step 4

**Interfaces:**

- Consumes: the `.btn*` classes in `ui/styles/components/buttons.css` (unchanged).
- Produces: `<AppButton>` with props `variant?: 'primary' | 'secondary' | 'danger'` (default `undefined` → bare `.btn`), `size?: 'sm'`, `ghost?: boolean`, `loading?: boolean`, `disabled?: boolean`, `type?: 'button' | 'submit'` (default `'button'`); default slot for label content; emits nothing (native `click` falls through via `$attrs`).

- [ ] **Step 1: Write the failing test**

Create `ui/components/shared/app-button.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AppButton from './app-button.vue'

describe('app-button', () => {
  it('renders the bootstrap class names for a ghost secondary small button', () => {
    const wrapper = mount(AppButton, {
      props: { variant: 'secondary', size: 'sm', ghost: true },
      slots: { default: 'Reset' },
    })
    expect(wrapper.classes()).toEqual(
      expect.arrayContaining(['btn', 'btn-secondary', 'btn-sm', 'btn-ghost'])
    )
    expect(wrapper.text()).toBe('Reset')
  })

  it('disables itself and shows a spinner while loading', () => {
    const wrapper = mount(AppButton, { props: { loading: true }, slots: { default: 'Save' } })
    expect(wrapper.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.btn-spinner').exists()).toBe(true)
  })

  it('defaults to a non-submitting button', () => {
    const wrapper = mount(AppButton, { slots: { default: 'Cancel' } })
    expect(wrapper.attributes('type')).toBe('button')
    expect(wrapper.classes()).toContain('btn')
  })
})
```

- [ ] **Step 2: Run it and verify it fails**

```bash
npm test -- --run ui/components/shared/app-button.test.ts
```

Expected: FAIL — `Failed to resolve import "./app-button.vue"`.

- [ ] **Step 3: Write the component**

Create `ui/components/shared/app-button.vue`:

```vue
<template>
  <button :type="type" :class="classes" :disabled="disabled || loading">
    <span v-if="loading" class="btn-spinner" aria-hidden="true"></span>
    <slot />
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'danger'
    size?: 'sm'
    ghost?: boolean
    loading?: boolean
    disabled?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: undefined, size: undefined, type: 'button' }
)

const classes = computed(() => [
  'btn',
  props.variant ? `btn-${props.variant}` : undefined,
  props.size ? `btn-${props.size}` : undefined,
  props.ghost ? 'btn-ghost' : undefined,
])
</script>
```

No `<style>` block: the `.btn*` rules stay global because `.btn-close` and `.btn-add-new` are still rendered raw elsewhere.

- [ ] **Step 4: Run the test and verify it passes**

```bash
npm test -- --run ui/components/shared/app-button.test.ts
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Replace the call sites**

Convert each `<button class="btn ...">` to `<AppButton ...>`, preserving every existing class, attribute, `v-if`, `@click`, `:disabled` and `data-testid`. Work through them in this order, committing after each file group so a regression is bisectable:

```
ui/components/diversification/allocation-table.vue     (10 sites)
ui/components/diversification/config-dialog.vue        (4)
ui/components/etf/logo-replacement-modal.vue           (3)
ui/components/etf/etf-breakdown.vue                    (3)
ui/components/shared/platform-filter.vue               (2)
ui/components/shared/confirm-dialog.vue                (2)
ui/components/portfolio/portfolio-actions.vue          (2)
ui/components/instruments/instrument-modal.vue         (2)
ui/components/calculator.vue                           (2)
ui/components/transactions/transactions-view.vue       (1)
ui/components/shared/crud-layout.vue                   (1)
ui/components/portfolio/chart-range-filter.vue         (1)
ui/components/portfolio-summary.vue                    (1)
ui/components/instruments/xirr-windows-modal.vue       (1)
ui/components/instruments/annual-windows-modal.vue     (1)
ui/components/diversification/allocation-card.vue      (1)
```

Skip these three — they render `.btn-close` or `.btn` variants the component does not cover:

- `ui/components/shared/modal-shell.vue` (`.btn-close`)
- `ui/components/shared/toast-container.vue` (`.btn-close`)
- `ui/components/shared/filter-toggle.vue` (`.platform-btn dropdown-toggle`, not a `.btn`)

Rules while converting:

- A button that carries extra utility classes keeps them: `<AppButton variant="primary" class="mt-3">`.
- A button whose classes do not decompose into the prop set (an unusual combination, an extra bespoke class) stays a raw `<button>`. Do not bend the component to fit one site.
- Preserve `data-testid` and `aria-*` attributes verbatim — E2E and unit tests select on them.

- [ ] **Step 6: Verify nothing moved**

```bash
npm run lint-format
npm test -- --run
npm run visual
```

Expected: all green. Buttons appear in nearly every screenshot; a single lost class shows up immediately. Revert `ui/models/generated/domain-models.ts` churn.

- [ ] **Step 7: Commit**

```bash
git add ui/components/shared/app-button.vue ui/components/shared/app-button.test.ts ui/components
git commit -m "Extract the shared button component"
```

### Task 8: Extract `alert-message.vue`

14 alert sites across 9 files, in 3 variants (`danger`, `info`, `warning`), one of them dismissible.

**Files:**

- Create: `ui/components/shared/alert-message.vue`
- Create: `ui/components/shared/alert-message.test.ts`
- Modify: the 9 files listed in Step 5

**Interfaces:**

- Consumes: `.alert*` classes in `ui/styles/components/feedback.css` (unchanged).
- Produces: `<AlertMessage>` with props `variant: 'danger' | 'info' | 'warning'` (required), `dismissible?: boolean`; default slot for the message; emits `dismiss` when the close button is clicked. Always renders `role="alert"`.

- [ ] **Step 1: Write the failing test**

Create `ui/components/shared/alert-message.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AlertMessage from './alert-message.vue'

describe('alert-message', () => {
  it('renders the bootstrap alert class names for its variant', () => {
    const wrapper = mount(AlertMessage, {
      props: { variant: 'danger' },
      slots: { default: 'Ei õnnestunud laadida' },
    })
    expect(wrapper.classes()).toEqual(expect.arrayContaining(['alert', 'alert-danger']))
    expect(wrapper.attributes('role')).toBe('alert')
    expect(wrapper.text()).toBe('Ei õnnestunud laadida')
  })

  it('emits dismiss when the close button is clicked', async () => {
    const wrapper = mount(AlertMessage, {
      props: { variant: 'info', dismissible: true },
      slots: { default: 'Teade' },
    })
    expect(wrapper.classes()).toContain('alert-dismissible')
    await wrapper.find('.btn-close').trigger('click')
    expect(wrapper.emitted('dismiss')).toHaveLength(1)
  })

  it('omits the close button when it cannot be dismissed', () => {
    const wrapper = mount(AlertMessage, {
      props: { variant: 'warning' },
      slots: { default: 'Hoiatus' },
    })
    expect(wrapper.find('.btn-close').exists()).toBe(false)
    expect(wrapper.classes()).not.toContain('alert-dismissible')
  })
})
```

- [ ] **Step 2: Run it and verify it fails**

```bash
npm test -- --run ui/components/shared/alert-message.test.ts
```

Expected: FAIL — `Failed to resolve import "./alert-message.vue"`.

- [ ] **Step 3: Write the component**

Create `ui/components/shared/alert-message.vue`:

```vue
<template>
  <div :class="classes" role="alert">
    <slot />
    <button
      v-if="dismissible"
      type="button"
      class="btn-close"
      aria-label="Close"
      @click="emit('dismiss')"
    ></button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  variant: 'danger' | 'info' | 'warning'
  dismissible?: boolean
}>()

const emit = defineEmits<{ dismiss: [] }>()

const classes = computed(() => [
  'alert',
  `alert-${props.variant}`,
  props.dismissible ? 'alert-dismissible' : undefined,
])
</script>
```

- [ ] **Step 4: Run the test and verify it passes**

```bash
npm test -- --run ui/components/shared/alert-message.test.ts
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Replace the call sites**

```
ui/components/portfolio-summary.vue:32,36,43,79        (4 sites; :43 is alert-dismissible)
ui/components/diversification/config-dialog.vue:46,60   (2)
ui/components/shared/data-table.vue:11,15               (2)
ui/components/diversification/diversification-calculator.vue:78  (1)
ui/components/instruments/xirr-windows-modal.vue:12     (1)
ui/components/instruments/annual-windows-modal.vue:12   (1)
ui/components/etf/etf-breakdown-table.vue:5             (1)
ui/components/etf/logo-replacement-modal.vue:11         (1)
```

`ui/components/portfolio-summary.vue:149` builds an alert through a render function (`{ class: 'alert alert-warning', role: 'alert' }`), not a template. Leave it as it is — converting a render-function call site is not worth the churn, and its class string is already correct.

Preserve utility classes on each site (`mt-4`, `mb-0`, `m-6!`, `mt-3`) by passing them through `class`.

- [ ] **Step 6: Verify nothing moved**

```bash
npm run lint-format
npm test -- --run
npm run visual
npm run check-unused
```

Expected: all green. `ui/tests/visual/states.spec.ts:248,257` screenshots `.alert-info` and `.alert-danger` directly — that is the gate for this task. `check-unused` is the gate for Phase D as a whole: it catches an extracted component that ended up with an unused export or a call site left behind. Note that knip exits 1 locally when `.claude/worktrees/` copies exist — if it reports duplicates of files you did not touch, that is the known local-only noise, not a real finding.

- [ ] **Step 7: Commit and open the PR**

```bash
git add ui/components/shared/alert-message.vue ui/components/shared/alert-message.test.ts ui/components
git commit -m "Extract the shared alert component"
gh pr create --title "Extract shared button and alert components" --body "$(cat <<'EOF'
## Summary

- Add `app-button.vue` and `alert-message.vue`, replacing 35 hand-written call sites
- Both render the existing class names, so every E2E, unit and visual selector is unchanged
- `.btn-close`, `.btn-add-new` and `.platform-btn` stay raw where they are rendered directly

## Test plan

- [ ] `npm test -- --run` green including the two new component test files
- [ ] `npm run visual` green against unchanged baselines
- [ ] `npm run lint-format` and `npm run check-unused` green

Closes #XXX
EOF
)"
```

---

## Phase C — Retire the legacy tokens

Branch: `feature/<issue>-retire-legacy-tokens`. Zero visual delta, but this is the phase the pixel gate cannot police on its own: `maxDiffPixels: 0` runs at Playwright's default per-pixel `threshold` of 0.2, which is blind to a close colour swap. Two extra gates compensate: every swap is proven identical by construction from `theme.css`, and a `threshold: 0` double-run compares actual pixels.

### Task 9: Swap the gray ramp

Five shades map exactly onto Statement tokens. Four have no equivalent and keep their exact values under lightness-named tokens.

| Old                | Value                   | New                           |
| ------------------ | ----------------------- | ----------------------------- |
| `--color-gray-100` | `oklch(0.975 0.007 85)` | `--color-surface-hover`       |
| `--color-gray-200` | `oklch(0.93 0.008 85)`  | `--color-hairline-soft` (new) |
| `--color-gray-300` | `oklch(0.905 0.008 85)` | `--color-hairline`            |
| `--color-gray-400` | `oklch(0.855 0.01 85)`  | `--color-hairline-strong`     |
| `--color-gray-500` | `oklch(0.55 0.014 60)`  | `--color-ink-55` (new)        |
| `--color-gray-600` | `oklch(0.5 0.014 60)`   | `--color-ink-soft`            |
| `--color-gray-700` | `oklch(0.4 0.014 60)`   | `--color-ink-40` (new)        |
| `--color-gray-800` | `oklch(0.32 0.013 60)`  | `--color-ink-32` (new)        |
| `--color-gray-900` | `oklch(0.24 0.012 60)`  | `--color-ink`                 |

The three new ink tokens are named for their lightness because they are mechanical holdovers, not roles — the number states exactly what they are and marks them as consolidation candidates. Do not invent role names for them.

**Files:**

- Modify: `ui/styles/theme.css:67-75`
- Modify: 14 files with `var(--color-gray-*)` references (75 sites)
- Modify: 6 files with `text-gray-600` / `bg-gray-100` utility classes (14 sites)

**Interfaces:**

- Consumes: existing Statement tokens in `theme.css`.
- Produces: `--color-hairline-soft`, `--color-ink-55`, `--color-ink-40`, `--color-ink-32` in `@theme static`; zero `--color-gray-*` anywhere in `ui/`.

- [ ] **Step 1: Add the three new ink tokens and the soft hairline**

In `ui/styles/theme.css`, add to the hairline group — after `--color-hairline-strong` on line 27, before the `--color-control-border` alias on line 28:

```css
--color-hairline-soft: oklch(0.93 0.008 85);
```

and to the ink group, after `--color-ink-faint` on line 32 and before the `--color-ink-muted` alias on line 33:

```css
--color-ink-55: oklch(0.55 0.014 60);
--color-ink-40: oklch(0.4 0.014 60);
--color-ink-32: oklch(0.32 0.013 60);
```

Do **not** delete the gray ramp yet — both names must resolve while the swap is in progress.

- [ ] **Step 2: Swap every `var(--color-gray-*)` reference**

Apply the mapping table above across these files:

```
ui/app.vue:60,65
ui/plugins/chart.ts:47
ui/styles/components/feedback.css:54,61
ui/styles/components/forms.css:168
ui/styles/components/buttons.css:68,72,104,116
ui/styles/components/mobile-cards.css:5,12,26,35
ui/components/diversification/allocation-table.vue  (12 sites)
ui/components/diversification/allocation-card.vue   (13 sites)
ui/components/shared/data-table.vue:205,227,243,304
ui/components/transactions/transaction-table.vue:250,263,273,289,306,315,327
ui/components/instruments/instrument-table.vue      (14 sites)
ui/components/etf/etf-breakdown-table.vue:587
```

`ui/components/shared/modal-shell.vue` also holds `--color-gray-100` if Task 4 moved `modals.css:28` into it — check there too.

`ui/plugins/chart.ts:47` reads the token at runtime with `getPropertyValue('--color-gray-700')`. That is a **string literal in TypeScript**, not CSS: it must become `'--color-ink-40'` or the chart silently loses its colour. A CSS-only find-and-replace will miss it.

- [ ] **Step 3: Swap the generated utility classes**

These are class names in templates, invisible to a `var()` grep. Deleting the token stops generating the class, so each must move in the same commit:

```
text-gray-600 → text-ink-soft   (13 sites)
  ui/components/diversification/allocation-table.vue:238,242,245,248,251,278,282,376
  ui/components/etf/etf-breakdown-table.vue:115,118,381
  ui/components/shared/currency-split-card.vue:7
  ui/components/transactions/transaction-table.vue:116,126,143

bg-gray-100 → bg-surface-hover  (1 site)
  ui/app.vue:16
```

- [ ] **Step 4: Delete the gray ramp**

Remove lines 67-75 of `ui/styles/theme.css` — all nine `--color-gray-*` declarations.

- [ ] **Step 5: Prove zero survivors**

```bash
grep -rn "color-gray-\|text-gray-\|bg-gray-\|border-gray-" ui --exclude-dir=node_modules
```

Expected: **no output**. Any hit is a broken reference that now resolves to nothing.

- [ ] **Step 6: Verify no visual change**

```bash
npm run lint-format
npm test -- --run
npm run visual
```

Expected: all green. Then run the colour-sensitive gate below before committing.

- [ ] **Step 7: Run the threshold-0 double-run**

The standard gate cannot see a colour-only mistake. Create `playwright.pixel.config.ts` at the repo root — untracked, deleted at the end of this task:

```ts
import base from './playwright.config'
import { defineConfig } from '@playwright/test'

export default defineConfig({
  ...base,
  snapshotPathTemplate: 'pixel-baseline/{arg}-{projectName}{ext}',
  expect: {
    ...base.expect,
    toHaveScreenshot: { ...base.expect?.toHaveScreenshot, threshold: 0, maxDiffPixels: 0 },
  },
})
```

Then:

```bash
git stash
npm run visual -- --config=playwright.pixel.config.ts --update-snapshots
git stash pop
npm run visual -- --config=playwright.pixel.config.ts
```

Expected: the second run PASSES with zero differing pixels at `threshold: 0`. A failure here means a swap changed a real colour — find it in the diff report, fix the mapping, repeat.

Clean up before committing:

```bash
rm -rf pixel-baseline playwright.pixel.config.ts
```

- [ ] **Step 8: Commit**

```bash
git add ui/
git commit -m "Replace the gray ramp with Statement tokens"
```

### Task 10: Retire the remaining aliases

Five alias families, all pure `var()` indirections, so every swap is provably identity.

| Old                             | Resolves to               | New                  |
| ------------------------------- | ------------------------- | -------------------- |
| `--color-signal-indigo`         | `var(--color-brass)`      | `--color-brass`      |
| `--color-signal-indigo-deep`    | `var(--color-brass-deep)` | `--color-brass-deep` |
| `--color-control-graphite`      | `var(--color-ink-soft)`   | `--color-ink-soft`   |
| `--color-control-graphite-deep` | `var(--color-ink)`        | `--color-ink`        |
| `--color-status-success`        | `var(--color-gain)`       | `--color-gain`       |
| `--color-status-danger`         | `var(--color-loss)`       | `--color-loss`       |
| `--color-status-info`           | `var(--color-notice)`     | `--color-notice`     |
| `--color-status-warning`        | `var(--color-brass)`      | `--color-brass`      |
| `--color-ink-muted`             | `var(--color-ink-soft)`   | `--color-ink-soft`   |
| `--color-body-secondary`        | `var(--color-ink-soft)`   | `--color-ink-soft`   |

**Files:**

- Modify: `ui/styles/theme.css:33-34,50-58`
- Modify: 20 files across `var()` and utility-class references

**Interfaces:**

- Consumes: `theme.css` as left by Task 9.
- Produces: zero references to any of the ten aliases; `@theme static` shorter by 10 declarations.

- [ ] **Step 1: Swap the `signal-indigo` references (14 sites)**

```
ui/app.vue:66
ui/styles/components/forms.css:26,137
ui/components/nav-bar.vue:104,117
ui/components/shared/data-table.vue:353
ui/styles/components/buttons.css:55,59
ui/components/shared/loading-spinner.vue:56
ui/components/diversification/config-dialog.vue:242
ui/components/instruments/instrument-table.vue:494
ui/components/etf/logo-replacement-modal.vue:185
```

Plus the two **utility-class** sites, which no `var()` grep finds:

```
ui/components/diversification/diversification-calculator.vue:23,73
  class="spinner-border text-signal-indigo" → class="spinner-border text-brass"
```

- [ ] **Step 2: Swap the `control-graphite` references (6 sites)**

```
ui/styles/components/controls.css:15,32,33   → var(--color-ink-soft)
ui/styles/components/controls.css:38,39      → var(--color-ink)
ui/styles/components/feedback.css:100        → var(--color-ink-soft)
```

If Task 6 moved the toast block into `toast-container.vue`, `feedback.css:100` now lives there instead — check both.

- [ ] **Step 3: Swap the `status-*` references (4 sites)**

All four are the `--toast-bg` variants, in `toast-container.vue` after Task 6 (originally `feedback.css:125,129,133,137`):

```
--toast-bg: var(--color-status-success) → var(--color-gain)
--toast-bg: var(--color-status-info)    → var(--color-notice)
--toast-bg: var(--color-status-danger)  → var(--color-loss)
--toast-bg: var(--color-status-warning) → var(--color-brass)
```

- [ ] **Step 4: Swap the `ink-muted` references (13 sites)**

```
ui/styles/components/controls.css:7,123
ui/styles/components/forms.css:32
ui/components/diversification/breakdown-card.vue:45
ui/components/diversification/diversification-calculator.vue:392
ui/components/shared/stat-card.vue:22
ui/components/transactions/transactions-view.vue:250
ui/components/instruments/instruments-view.vue:249,310
ui/components/etf/etf-breakdown.vue:515
ui/components/etf/etf-breakdown-table.vue:415,473,505
```

All become `var(--color-ink-soft)`.

- [ ] **Step 5: Swap the `body-secondary` utility class (10 sites)**

`--color-body-secondary` has **zero** `var()` references but ten live consumers of its generated utility. Deleting the token without this step silently strips the colour from all ten:

```
text-body-secondary → text-ink-soft
  ui/components/diversification/config-dialog.vue:11,25,51
  ui/components/etf/etf-breakdown-header.vue:2
  ui/components/etf/logo-replacement-modal.vue:14,35
  ui/components/instruments/annual-windows-modal.vue:28,34
  ui/components/instruments/xirr-windows-modal.vue:28,34
  ui/components/nav-bar.vue:16
```

- [ ] **Step 6: Delete the alias declarations**

From `ui/styles/theme.css`, remove lines 33-34 (`--color-ink-muted`, `--color-body-secondary`) and lines 50-58 (`--color-signal-indigo`, `--color-signal-indigo-deep`, `--color-control-graphite`, `--color-control-graphite-deep`, and the four `--color-status-*`).

- [ ] **Step 7: Prove zero survivors**

```bash
grep -rn "signal-indigo\|control-graphite\|color-status-\|ink-muted\|body-secondary" ui --exclude-dir=node_modules
```

Expected: **no output**.

- [ ] **Step 8: Verify no visual change, including the colour-blind case**

```bash
npm run lint-format
npm test -- --run
npm run visual
```

Then repeat the threshold-0 double-run exactly as in Task 9 Step 7. Expected: PASS at `threshold: 0`. Delete `pixel-baseline/` and `playwright.pixel.config.ts` afterwards.

- [ ] **Step 9: Commit**

```bash
git add ui/
git commit -m "Delete the legacy colour aliases"
```

### Task 11: Deduplicate the repeated black tints

16 pure-black tints remain. They cannot move onto Statement shadow/hairline tokens without changing pixels — every such token is ink-hue `oklch(0.24 0.012 60 / α)`, not black. So this task only removes genuine duplication and modernizes syntax; the 9 one-off values stay and get recorded as open debt.

**Files:**

- Modify: `ui/styles/theme.css` (2 new tokens)
- Modify: `ui/styles/components/buttons.css:89,117`, `ui/components/shared/data-table.vue:318`
- Modify: `ui/components/etf/etf-breakdown-chart.vue:2`, `ui/components/etf/etf-breakdown-table.vue:2`
- Modify: `ui/components/shared/modal-shell.vue:119`, `ui/components/instruments/instruments-view.vue:286`

**Interfaces:**

- Consumes: `theme.css` as left by Task 10.
- Produces: `--color-surface-tint: rgb(0 0 0 / 0.02)` and `--shadow-panel: 0 0.125rem 0.25rem rgb(0 0 0 / 0.075)`, generating `bg-surface-tint` and `shadow-panel` utilities.

- [ ] **Step 1: Add the two tokens**

In `ui/styles/theme.css`, add `--color-surface-tint: rgb(0 0 0 / 0.02);` to the surface group (after line 24) and `--shadow-panel: 0 0.125rem 0.25rem rgb(0 0 0 / 0.075);` to the shadow group (after line 98).

- [ ] **Step 2: Replace the three `rgb(0 0 0 / 0.02)` sites**

```
ui/styles/components/buttons.css:89   background: rgb(0 0 0 / 0.02) → var(--color-surface-tint)
ui/styles/components/buttons.css:117  background: rgb(0 0 0 / 0.02) → var(--color-surface-tint)
ui/components/shared/data-table.vue:318  background-color: rgb(0 0 0 / 0.02) → var(--color-surface-tint)
```

- [ ] **Step 3: Replace the two duplicated card shadows**

Both files open with the identical arbitrary-value shadow:

```
ui/components/etf/etf-breakdown-chart.vue:2
ui/components/etf/etf-breakdown-table.vue:2
  class="card border-0! shadow-[0_0.125rem_0.25rem_rgb(0_0_0/0.075)]"
  →
  class="card border-0! shadow-panel"
```

- [ ] **Step 4: Modernize the two legacy `rgba()` sites**

```
ui/components/shared/modal-shell.vue:119     rgba(0, 0, 0, 0.5) → rgb(0 0 0 / 0.5)
ui/components/instruments/instruments-view.vue:286  rgba(0, 0, 0, 0.2) → rgb(0 0 0 / 0.2)
```

These are identical colours in modern space syntax — no computed change.

- [ ] **Step 5: Record the remaining debt in DESIGN.md**

In the debt census section of `DESIGN.md`, replace the "16 neutral black tints" entry with the 9 that survive and why:

> Nine one-off pure-black tints remain (`navigation.css` nav-link 0.65 — now in `nav-bar.vue`; `surfaces.css` card border 0.05; `modals.css` modal border 0.175 — now in `modal-shell.vue`; `motion.css` skeleton 0.06/0.1/0.06; `buttons.css` inset 0.04; `mobile-cards.css` 0.03; `calculator.vue` 0.08). Every Statement shadow and hairline token is ink-hue, so moving these onto tokens is a visual change, not a rename — it needs a re-baseline and is out of scope for the token pass.

- [ ] **Step 6: Verify no visual change**

```bash
npm run lint-format
npm test -- --run
npm run visual
```

Then the threshold-0 double-run as in Task 9 Step 7. Expected: PASS. `shadow-panel` must render byte-identically to the arbitrary value it replaced — if it does not, Tailwind is normalizing the shadow differently and the arbitrary value should be restored.

- [ ] **Step 7: Refresh DESIGN.md citations and commit**

Every `file:line` citation in `DESIGN.md` pointing into a file Phases B, C or D moved is now stale. Walk the citations, re-resolve each one, and correct it. Then:

```bash
git add ui/ DESIGN.md
git commit -m "Tokenize the repeated black tints"
gh pr create --title "Retire the legacy colour aliases" --body "$(cat <<'EOF'
## Summary

- Replace the nine-step gray ramp with Statement tokens; four shades with no equivalent keep their exact values as `--color-hairline-soft`, `--color-ink-55/40/32`
- Delete `signal-indigo`, `control-graphite`, `status-*`, `ink-muted` and `body-secondary` aliases, sweeping both `var()` and generated-utility references
- Tokenize the repeated black tints as `--color-surface-tint` and `--shadow-panel`; record the nine remaining one-offs as open debt

Every swap is a pure alias indirection, so values are identical by construction. Verified additionally with a `threshold: 0` pixel run, since the standard gate is blind to close colours.

## Test plan

- [ ] `grep` proves zero surviving references to every deleted token
- [ ] `npm run visual` green against unchanged baselines
- [ ] `threshold: 0` double-run green (stashed vs applied)
- [ ] `npm run lint-format` and `npm test -- --run` green

Closes #XXX
EOF
)"
```

---

## Phase E — Curated 2026 polish

Branch: `feature/<issue>-2026-polish`. **The only phase that changes pixels.** One PR, one polish change per commit, baselines updated in the same commit as the change that causes them, so every screenshot diff is attributable to exactly one decision.

### Task 12: Audit the current surface

**Files:** none modified.

**Interfaces:**

- Consumes: the app as left by Phase C.
- Produces: a written before-state the final task compares against.

- [ ] **Step 1: Run the impeccable audit**

Invoke the `impeccable` skill against the running app (`npm run dev:ui`, port 61234) across all 6 routes. Capture its findings for the four curated items only: spinner weight, badge weight, card/table border treatments, label-gesture variants. Record anything else it raises in the PR body as a future candidate — do not act on it. The curated list is fixed.

The chrome-devtools MCP tools are available for live iteration on the three polish tasks: tweak a value in the browser, look at it, then commit the value you settled on. That is a faster loop than edit → re-baseline → inspect PNG, and it is the intended way to pick the spinner arc width in Task 13 and confirm the card border in Task 14.

- [ ] **Step 2: Confirm the baseline is clean before touching anything**

```bash
npm run visual
```

Expected: green. If it is red before any change, stop — the baselines have drifted from `main` and that must be resolved first, or every diff in this phase becomes unattributable.

### Task 13: Thin the spinner and lighten the badge

**Files:**

- Modify: `ui/styles/components/feedback.css:48` (badge weight), `:70-86` (spinner)

**Interfaces:**

- Consumes: nothing.
- Produces: `.spinner-border` with a 2px arc; `.badge` at weight 550.

- [ ] **Step 1: Thin the spinner**

The 0.25em border is the loudest remaining Bootstrap tell. In `ui/styles/components/feedback.css`, change `.spinner-border`:

```css
.spinner-border {
  display: inline-block;
  flex-shrink: 0;
  width: 2rem;
  height: 2rem;
  vertical-align: -0.125em;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: spinner-border 0.75s linear infinite;
}
```

and `.spinner-border-sm`:

```css
.spinner-border-sm {
  width: 1rem;
  height: 1rem;
  border-width: 1.5px;
}
```

Leave the `@keyframes spinner-border` and the `prefers-reduced-motion` override in `base.css:150-155` alone — the spinner must keep animating under reduced motion.

- [ ] **Step 2: Lighten the badge**

Change `.badge` `font-weight: 700` to `font-weight: 550`. Leave `.platform-tags .badge` at `font-weight: 500` — it is already lighter and is a deliberate override.

- [ ] **Step 3: Re-baseline and inspect the diff**

```bash
npm run visual:update
git status --short docs/superpowers/baseline/
```

Expected: only baselines containing a spinner or a badge changed. If an unrelated baseline moved, investigate before committing — that is drift, not this change.

- [ ] **Step 4: Review the updated images**

Open the changed PNGs. Confirm the spinner reads as a thin arc and the badge is visibly lighter but still legible at 0.75em. If the 2px arc looks too faint at `2rem`, 2.5px is acceptable — but pick one value and use it everywhere.

- [ ] **Step 5: Commit**

```bash
git add ui/styles/components/feedback.css docs/superpowers/baseline/
git commit -m "Thin the spinner and lighten the badge"
```

### Task 14: Collapse the border treatments to two

Three treatments exist today: `.card` uses `rgb(0 0 0 / 0.05)`, `.card-shell` uses `var(--color-hairline)`, and the table family uses `var(--color-hairline-strong)`. Standardize on the token pair: `--color-hairline` for interior rules, `--color-hairline-strong` for outer edges. This also retires one of the nine surviving black tints.

**Files:**

- Modify: `ui/styles/components/surfaces.css:9`

**Interfaces:**

- Consumes: `--color-hairline` from `theme.css`.
- Produces: `.card` bordered with `var(--color-hairline)`, matching `.card-shell`.

- [ ] **Step 1: Swap the card border**

In `ui/styles/components/surfaces.css`, change line 9:

```css
border: 1px solid var(--color-hairline);
```

This is a visible change: `rgb(0 0 0 / 0.05)` over the paper background is a cooler, fainter line than `oklch(0.905 0.008 85)`. That is the intended modernization — the card edge now matches `.card-shell` and the paper hue instead of being a neutral gray wash.

- [ ] **Step 2: Verify the two treatments are all that remain**

```bash
grep -rn "border.*hairline\|border.*rgb(0 0 0" ui/styles ui/components --include=*.css --include=*.vue
```

Expected: only `--color-hairline` and `--color-hairline-strong` on card/table/panel surfaces. Component-local borders that are deliberately different (inputs, chips) are out of scope — do not touch them.

- [ ] **Step 3: Re-baseline and inspect**

```bash
npm run visual:update
git status --short docs/superpowers/baseline/
```

Expected: every baseline containing a `.card` changed, and only by the border colour. Open two or three and confirm the change is a subtle warmer hairline, not a heavier line.

- [ ] **Step 4: Commit**

```bash
git add ui/styles/components/surfaces.css docs/superpowers/baseline/
git commit -m "Unify the card border on the hairline token"
```

### Task 15: Normalize the label gesture

Nine label declarations exist in five distinct specs. The canonical gesture is the one in `surfaces.css` `.table thead th`: `var(--text-label)` / `600` / `0.05em` / uppercase.

| File                                                         | Current                       | Action                                       |
| ------------------------------------------------------------ | ----------------------------- | -------------------------------------------- |
| `ui/styles/components/surfaces.css:42-50`                    | `--text-label` / 600 / 0.05em | canonical — leave                            |
| `ui/styles/components/mobile-cards.css:31-36`                | 600 / 0.05em                  | add `font-size: var(--text-label)` if absent |
| `ui/components/shared/stat-card.vue:19-23`                   | 500 / 0.05em                  | weight → 600                                 |
| `ui/components/diversification/breakdown-card.vue:42-46`     | 500 / 0.05em                  | weight → 600                                 |
| `ui/components/calculator.vue:154-158`                       | 500 / **0.5px**               | weight → 600, spacing → 0.05em               |
| `ui/components/diversification/allocation-table.vue:638-642` | 500 / **0.5px**               | weight → 600, spacing → 0.05em               |
| `ui/components/diversification/allocation-card.vue:244-248`  | **0.6875rem** / 0.025em       | size → `var(--text-label)`, spacing → 0.05em |
| `ui/components/transactions/transaction-table.vue:303-307`   | **0.6875rem** / 0.025em       | size → `var(--text-label)`, spacing → 0.05em |
| `ui/components/transactions/transactions-view.vue:246-250`   | `--text-label` / 0.05em       | add `font-weight: 600` if absent             |

Colours stay as they are — this task normalizes the gesture (size, weight, spacing), not the palette.

**Files:** the nine listed above.

**Interfaces:**

- Consumes: `--text-label: 0.75rem` from `theme.css`.
- Produces: one label gesture — `font-size: var(--text-label); font-weight: 600; letter-spacing: 0.05em; text-transform: uppercase`.

- [ ] **Step 1: Apply the table**

Edit each file per the Action column. Do not extract a shared `.label` class — these live in eight different components with different colours and layout context, and a shared class would need overriding at nearly every site. Normalizing the values is the whole job.

- [ ] **Step 2: Verify one gesture remains**

```bash
grep -rn -A3 "text-transform: uppercase" ui/styles ui/components --include=*.css --include=*.vue | grep -E "letter-spacing|font-weight|font-size"
```

Expected: every label declaration reads `var(--text-label)` / `600` / `0.05em`. Any `0.5px`, `0.025em` or `0.6875rem` remaining is a miss.

- [ ] **Step 3: Re-baseline and inspect**

```bash
npm run visual:update
git status --short docs/superpowers/baseline/
```

Labels appear on nearly every route, so expect a broad but shallow diff. Open the mobile viewport shots specifically — `0.6875rem` → `0.75rem` is a real size bump on the two table components and could reflow a narrow column. If anything wraps that did not before, reconsider that one row of the table rather than shipping a reflow.

- [ ] **Step 4: Commit**

```bash
git add ui/ docs/superpowers/baseline/
git commit -m "Normalize the label gesture across components"
```

### Task 16: Close out the phase

**Files:**

- Modify: `DESIGN.md`

**Interfaces:**

- Consumes: everything above.
- Produces: DESIGN.md with the Phase 2 backlog marked done, a refreshed debt census, and accurate citations.

- [ ] **Step 1: Re-run the impeccable audit**

Run the same audit as Task 12 against the polished app. Confirm the four curated items are resolved. Record the before/after in the PR body.

- [ ] **Step 2: Verify the whole gate is green**

```bash
npm run lint-format
npm test -- --run
npm run visual
npm run check-unused
```

Expected: all green, `npm run visual` now passing against the **updated** baselines. `ui/tests/visual/palette.spec.ts` is the AA-contrast gate — the badge at weight 550 and the thinner spinner must still pass it.

- [ ] **Step 3: Update DESIGN.md**

- Mark the Phase 2 backlog complete: legacy aliases deleted, gray ramp retired, label gesture normalized.
- Update the debt census: the nine surviving black tints (from Task 11), the four orphan ink/hairline tones as consolidation candidates, and the `.card`/`.card-shell` pair as the two remaining border treatments.
- Re-resolve every `file:line` citation touched by Phases B–E. `modals.css` and `navigation.css` no longer exist; their rules are in `modal-shell.vue`, `nav-bar.vue` and `filter-toggle.vue`.

- [ ] **Step 4: Commit and open the PR**

```bash
git add DESIGN.md
git commit -m "Record the polish pass in DESIGN.md"
gh pr create --title "Retire the last Bootstrap tells" --body "$(cat <<'EOF'
## Summary

- Thin the spinner from a 0.25em border to a 2px arc and drop the badge from weight 700 to 550
- Unify the card border on `--color-hairline`, collapsing three border treatments to two
- Normalize nine label declarations onto one gesture: `--text-label` / 600 / 0.05em / uppercase
- Update DESIGN.md: Phase 2 backlog closed, debt census and citations refreshed

Baselines are updated in this PR — one polish change per commit, so each screenshot diff maps to exactly one decision.

## Test plan

- [ ] `npm run visual` green against the updated baselines
- [ ] `palette.spec.ts` AA-contrast and focus-ring checks green
- [ ] `npm run lint-format`, `npm test -- --run`, `npm run check-unused` green
- [ ] Impeccable audit rerun confirms all four curated items resolved

Closes #XXX
EOF
)"
```

---

## Verification Summary

| Phase | Visual expectation                           | Extra gate                                                   |
| ----- | -------------------------------------------- | ------------------------------------------------------------ |
| A     | `npm run visual` green, baselines untouched  | `./gradlew test` + `npm run test:e2e`                        |
| B     | `npm run visual` green, baselines untouched  | 7 modal shots, toast states, `palette.spec` focus rings      |
| D     | `npm run visual` green, baselines untouched  | `states.spec` alert shots, 2 new component test files        |
| C     | `npm run visual` green, baselines untouched  | `threshold: 0` double-run; grep proves zero surviving tokens |
| E     | Baselines **updated**, one change per commit | `palette.spec` AA contrast; impeccable audit before/after    |

## Open Risks

- **Phase A blast radius is unknown until measured.** A working retry may reveal E2E failures beyond quick-dates. Task 2 Step 4 triages; if the list is long, report rather than absorb.
- **Scoped-style precedence (Phase B).** Rules moved into an SFC become unlayered and outrank `@layer`. Safe for whole-block moves into the sole rendering component; slotted content is the failure mode, and `:deep()` is the fix.
- **Utility-class blindness (Phase C).** A deleted `@theme` key stops generating its class. The 29 utility sites in Tasks 9 and 10 are the ones found; the grep gates in Task 9 Step 5 and Task 10 Step 7 catch anything missed.
- **Baseline drift (Phase E).** All 6 routes are stubbed, so baselines should be stable. If Task 12 Step 2 is red before any change, stop and resolve first.
