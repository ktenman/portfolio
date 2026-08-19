# Portfolio Summary Live Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the portfolio summary every 5 seconds, rolling the headline total and range-change numbers to their new values and flashing their background green or red on the way.

**Architecture:** The frontend already has the animation machinery (`useNumberTransition` for the rAF number roll, `.value-increase`/`.value-decrease` keyframes for the flash) built for the instruments table; this reuses both. Two changes make the poll meaningful rather than decorative: the backend starts warming the _filtered_ current-day summary entry in the job that already warms the unfiltered one, and only the two live queries (`current`, `range-change`) get a `refetchInterval`.

**Tech Stack:** Kotlin 2.3 / Spring Boot 4.0 / Spring Cache (backend), Vue 3.5 / TypeScript / TanStack Vue Query / Vitest (frontend), Atrium + MockK + JUnit 5 (backend tests).

## Global Constraints

- No code comments of any kind (only TypeScript `///` triple-slash directives are exempt). Code must be self-documenting.
- Kotlin: no blank lines inside method bodies; error/log messages are a single sentence with no trailing period; prefer `val`; use `runCatching` over try-catch; guard clauses over nesting.
- Kotlin tests: Atrium assertions (`expect(x).toEqual(y)`), backtick test names spelled as full English sentences, "cannot"/"dont" written without apostrophes.
- Frontend: TypeScript strict mode, Composition API, `const` unless reassignment is required, no `any`.
- File naming is kebab-case; never add `-improved`/`-new`/`-refactored` suffixes.
- Commit subjects: uppercase imperative verb, max 50 chars, NO prefixes (`feat:`, `fix:` are forbidden). No AI attribution lines of any kind in commits.
- After any UI change run both `npm run lint-format` and `npm test -- --run`.
- Existing behaviour that must not regress: `ui/components/portfolio/range-change-header.test.ts` asserts exact rendered text on mount (`'+€25,429.00 (+17.16%)'`) and asserts a flat range has classes exactly `['range-change']`.

---

## File Structure

**Backend**

- `src/main/kotlin/ee/tenman/portfolio/service/summary/PlatformSummaryCacheService.kt` — gains a `@CachePut` sibling to its existing `@Cacheable` current-day method. Owns the cache-key expressions.
- `src/main/kotlin/ee/tenman/portfolio/job/CurrentDaySummaryRefreshJob.kt` — gains two dependencies so it warms the filtered entry too. Owns _when_ warming happens.
- `src/test/kotlin/ee/tenman/portfolio/configuration/CurrentDaySummaryCacheTestConfiguration.kt` — gains beans so the new `@CachePut` can be exercised through a real Spring cache proxy.
- `src/test/kotlin/ee/tenman/portfolio/service/summary/PlatformSummaryCacheRefreshTest.kt` (new) — the load-bearing test that the `@CachePut` key and the `@Cacheable` key are the same string.
- `src/test/kotlin/ee/tenman/portfolio/job/CurrentDaySummaryRefreshJobTest.kt` — updated for the new constructor.

**Frontend**

- `ui/constants/api.ts` — one new interval constant.
- `ui/composables/use-flash-on-change.ts` (new) — maps a changing number to a transient flash class. Single responsibility, no knowledge of any DTO.
- `ui/composables/use-flash-on-change.test.ts` (new).
- `ui/styles/base.css` — the flash keyframes and classes move here so both the instruments table and the summary can use them.
- `ui/components/instruments/instrument-table.vue` — the moved CSS is deleted from its scoped block.
- `ui/composables/use-portfolio-summary-query.ts` — `refetchInterval` on two queries.
- `ui/components/portfolio/range-change-header.vue` — animates its own two numbers.
- `ui/components/portfolio-summary.vue` — animates the headline total.

**Why the backend task comes first:** without it the frontend poll returns an identical payload for ~3 minutes and then hits a cold 16-18s recompute past the 10s axios timeout, so the animations would be unobservable and the feature untestable by hand.

---

### Task 1: Warm the filtered current-day summary

**Files:**

- Modify: `src/main/kotlin/ee/tenman/portfolio/service/summary/PlatformSummaryCacheService.kt:17-22`
- Modify: `src/test/kotlin/ee/tenman/portfolio/configuration/CurrentDaySummaryCacheTestConfiguration.kt`
- Test: `src/test/kotlin/ee/tenman/portfolio/service/summary/PlatformSummaryCacheRefreshTest.kt` (create)

**Interfaces:**

- Consumes: existing `PlatformSummaryCacheService(summaryService: SummaryService)`, `SummaryService.getCurrentDaySummaryForPlatforms(platforms: List<Platform>): PortfolioDailySummary`, `RedisConfiguration.Companion.SUMMARY_CACHE`.
- Produces: `PlatformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(platforms: List<Platform>): PortfolioDailySummary` — used by Task 2.

**Background the implementer needs:** `@Cacheable` and `@CachePut` only agree if their `key` SpEL strings resolve identically. The existing read path uses `key = "'platform-current-' + #root.target.platformKey(#platforms)"`, where `platformKey` sorts platform names and joins them with commas. The new write path must copy that expression character for character — a mismatch writes a second, unread entry and silently warms nothing. The test below is what proves they match; it is the whole point of this task.

- [ ] **Step 1: Add the beans the test needs to the existing test configuration**

In `src/test/kotlin/ee/tenman/portfolio/configuration/CurrentDaySummaryCacheTestConfiguration.kt`, add an import for `PlatformSummaryCacheService` and one new bean method. Leave the existing beans untouched.

```kotlin
  @Bean
  fun platformSummaryCacheService(summaryService: SummaryService): PlatformSummaryCacheService =
    PlatformSummaryCacheService(summaryService)
```

The import to add alongside the existing `CurrentDaySummaryCacheService` import:

```kotlin
import ee.tenman.portfolio.service.summary.PlatformSummaryCacheService
```

- [ ] **Step 2: Write the failing test**

Create `src/test/kotlin/ee/tenman/portfolio/service/summary/PlatformSummaryCacheRefreshTest.kt`:

```kotlin
package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.CurrentDaySummaryCacheTestConfiguration
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import io.mockk.clearMocks
import io.mockk.every
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.cache.CacheManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [CurrentDaySummaryCacheTestConfiguration::class])
@ActiveProfiles("summary-cache-unit-test")
class PlatformSummaryCacheRefreshTest {
  @Resource
  private lateinit var platformSummaryCacheService: PlatformSummaryCacheService

  @Resource
  private lateinit var summaryService: SummaryService

  @Resource
  private lateinit var testCacheManager: CacheManager

  private val platforms = listOf(Platform.TRADING212, Platform.BINANCE)

  @BeforeEach
  fun setup() {
    clearMocks(summaryService)
    testCacheManager.getCache(SUMMARY_CACHE)?.clear()
  }

  @Test
  fun `should serve the refreshed summary from cache so the refresh and the read share a cache key`() {
    every { summaryService.getCurrentDaySummaryForPlatforms(platforms) } returns summaryOn(LocalDate.of(2024, 3, 11))
    platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms)
    every { summaryService.getCurrentDaySummaryForPlatforms(platforms) } returns summaryOn(LocalDate.of(2024, 3, 12))
    platformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(platforms)
    val served = platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms)
    expect(served.entryDate).toEqual(LocalDate.of(2024, 3, 12))
  }

  @Test
  fun `should populate the cache from a refresh so a later read cannot recompute`() {
    every { summaryService.getCurrentDaySummaryForPlatforms(platforms) } returns summaryOn(LocalDate.of(2024, 3, 11))
    platformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(platforms)
    every { summaryService.getCurrentDaySummaryForPlatforms(platforms) } returns summaryOn(LocalDate.of(2024, 3, 12))
    val served = platformSummaryCacheService.getCurrentDaySummaryForPlatforms(platforms)
    expect(served.entryDate).toEqual(LocalDate.of(2024, 3, 11))
  }

  @Test
  fun `should key the refreshed summary by platform so another platform set cannot read it`() {
    every { summaryService.getCurrentDaySummaryForPlatforms(platforms) } returns summaryOn(LocalDate.of(2024, 3, 11))
    platformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(platforms)
    val other = listOf(Platform.LHV)
    every { summaryService.getCurrentDaySummaryForPlatforms(other) } returns summaryOn(LocalDate.of(2024, 3, 12))
    val served = platformSummaryCacheService.getCurrentDaySummaryForPlatforms(other)
    expect(served.entryDate).toEqual(LocalDate.of(2024, 3, 12))
  }

  private fun summaryOn(date: LocalDate): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal.ZERO,
      xirrAnnualReturn = BigDecimal.ZERO,
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )
}
```

Note the second test asserts the _stale_ date on purpose: it proves the refresh wrote into the cache that the reader reads, because a reader that recomputed would return the 3/12 value.

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew test --tests "PlatformSummaryCacheRefreshTest"`
Expected: FAIL to compile with `Unresolved reference: refreshCurrentDaySummaryForPlatforms`.

- [ ] **Step 4: Add the `@CachePut` method**

In `PlatformSummaryCacheService.kt`, add the import `org.springframework.cache.annotation.CachePut`, then add this method directly beneath the existing `getCurrentDaySummaryForPlatforms`:

```kotlin
  @CachePut(
    value = [SUMMARY_CACHE],
    key = "'platform-current-' + #root.target.platformKey(#platforms)",
  )
  fun refreshCurrentDaySummaryForPlatforms(platforms: List<Platform>): PortfolioDailySummary =
    summaryService.getCurrentDaySummaryForPlatforms(platforms)
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests "PlatformSummaryCacheRefreshTest" --tests "PlatformSummaryCacheServiceTest" --tests "CurrentDaySummaryCacheServiceTest"`
Expected: PASS, all three classes.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/service/summary/PlatformSummaryCacheService.kt \
        src/test/kotlin/ee/tenman/portfolio/configuration/CurrentDaySummaryCacheTestConfiguration.kt \
        src/test/kotlin/ee/tenman/portfolio/service/summary/PlatformSummaryCacheRefreshTest.kt
git commit -m "Add cache refresh for platform current summary"
```

---

### Task 2: Warm the filtered entry on the existing 120s job

**Files:**

- Modify: `src/main/kotlin/ee/tenman/portfolio/job/CurrentDaySummaryRefreshJob.kt` (whole file)
- Test: `src/test/kotlin/ee/tenman/portfolio/job/CurrentDaySummaryRefreshJobTest.kt` (rewrite)

**Interfaces:**

- Consumes: `PlatformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(...)` from Task 1; existing `TransactionService.getDistinctPlatforms(): List<Platform>`; existing `CurrentDaySummaryCacheService.refreshCurrentDaySummary()`.
- Produces: nothing consumed by later tasks.

**Background the implementer needs:** the frontend always sends every platform it knows about, so the entry worth warming is the one keyed by _all_ distinct platforms. When there are no transactions yet, `getDistinctPlatforms()` returns an empty list; warming an empty-platform key is pointless work, so guard and skip. Each warm is wrapped in its own `runCatching` so a failure warming one entry does not skip the other. This job's constructor is changing from one parameter to three, which breaks the two existing tests — they are rewritten in Step 1.

- [ ] **Step 1: Rewrite the test file with the new constructor and new expectations**

Replace the entire contents of `src/test/kotlin/ee/tenman/portfolio/job/CurrentDaySummaryRefreshJobTest.kt`:

```kotlin
package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.service.summary.CurrentDaySummaryCacheService
import ee.tenman.portfolio.service.summary.PlatformSummaryCacheService
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CurrentDaySummaryRefreshJobTest {
  private val currentDayCache = mockk<CurrentDaySummaryCacheService>(relaxed = true)
  private val platformCache = mockk<PlatformSummaryCacheService>(relaxed = true)
  private val transactionService = mockk<TransactionService>()
  private val job = CurrentDaySummaryRefreshJob(currentDayCache, platformCache, transactionService)

  @Test
  fun `should refresh current day summary cache when scheduled refresh runs`() {
    every { transactionService.getDistinctPlatforms() } returns listOf(Platform.LHV)
    job.refresh()
    verify { currentDayCache.refreshCurrentDaySummary() }
  }

  @Test
  fun `should refresh the summary for every known platform so the filtered cache stays warm`() {
    val platforms = listOf(Platform.TRADING212, Platform.BINANCE)
    every { transactionService.getDistinctPlatforms() } returns platforms
    job.refresh()
    verify { platformCache.refreshCurrentDaySummaryForPlatforms(platforms) }
  }

  @Test
  fun `should dont refresh any platform summary when no transactions exist`() {
    every { transactionService.getDistinctPlatforms() } returns emptyList()
    job.refresh()
    verify(exactly = 0) { platformCache.refreshCurrentDaySummaryForPlatforms(any()) }
  }

  @Test
  fun `should not propagate failures when the cache refresh throws`() {
    every { transactionService.getDistinctPlatforms() } returns listOf(Platform.LHV)
    every { currentDayCache.refreshCurrentDaySummary() } throws RuntimeException("price provider unavailable")
    job.refresh()
    verify { currentDayCache.refreshCurrentDaySummary() }
  }

  @Test
  fun `should still refresh the unfiltered summary when resolving platforms throws`() {
    every { transactionService.getDistinctPlatforms() } throws RuntimeException("database unavailable")
    job.refresh()
    verify { currentDayCache.refreshCurrentDaySummary() }
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "CurrentDaySummaryRefreshJobTest"`
Expected: FAIL to compile — the constructor still takes one argument.

- [ ] **Step 3: Rewrite the job**

Replace the entire contents of `src/main/kotlin/ee/tenman/portfolio/job/CurrentDaySummaryRefreshJob.kt`:

```kotlin
package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.summary.CurrentDaySummaryCacheService
import ee.tenman.portfolio.service.summary.PlatformSummaryCacheService
import ee.tenman.portfolio.service.transaction.TransactionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class CurrentDaySummaryRefreshJob(
  private val currentDaySummaryCacheService: CurrentDaySummaryCacheService,
  private val platformSummaryCacheService: PlatformSummaryCacheService,
  private val transactionService: TransactionService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${scheduling.jobs.summary-interval:120000}")
  fun refresh() {
    runCatching { currentDaySummaryCacheService.refreshCurrentDaySummary() }
      .onFailure { log.warn("Failed to refresh current day summary cache", it) }
    runCatching { refreshForKnownPlatforms() }
      .onFailure { log.warn("Failed to refresh platform current day summary cache", it) }
  }

  private fun refreshForKnownPlatforms() {
    val platforms = transactionService.getDistinctPlatforms()
    if (platforms.isEmpty()) return
    platformSummaryCacheService.refreshCurrentDaySummaryForPlatforms(platforms)
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests "CurrentDaySummaryRefreshJobTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Verify nothing else constructed this job**

Run: `grep -rn "CurrentDaySummaryRefreshJob(" --include="*.kt" src/`
Expected: only the test file. If a production call site appears, update it to pass the two new dependencies before continuing.

- [ ] **Step 6: Run the architecture tests**

Run: `./gradlew test --tests "ArchitectureTest"`
Expected: PASS. This job now has three constructor dependencies; the suite caps service _method_ dependencies at 65, so this is well clear, but run it because the job package is covered by layer rules.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/job/CurrentDaySummaryRefreshJob.kt \
        src/test/kotlin/ee/tenman/portfolio/job/CurrentDaySummaryRefreshJobTest.kt
git commit -m "Keep filtered summary cache warm in refresh job"
```

---

### Task 3: The flash-on-change composable

**Files:**

- Create: `ui/composables/use-flash-on-change.ts`
- Test: `ui/composables/use-flash-on-change.test.ts`

**Interfaces:**

- Consumes: `vue` (`ref`, `watch`, `type Ref`).
- Produces: `useFlashOnChange(value: Ref<number | null | undefined>, threshold?: number): Ref<string>` — returns `'value-increase'`, `'value-decrease'`, or `''`. Used by Tasks 6 and 7.

**Background the implementer needs:** `useValueChangeAnimation` cannot be reused here — its field names are a hardcoded union of `InstrumentDto` keys. This is the same idea with no DTO knowledge. Two behaviours matter and are easy to get wrong:

1. **No flash on first arrival.** The value starts `null` while the query loads and then becomes a number. That is not a gain, and flashing the whole page green on every load is wrong. Seed the previous value and return without flashing.
2. **A threshold.** Floating-point noise and sub-cent drift should not flash. Default `0.001`, matching the instruments table; percentages that move in small decimals pass a smaller one.

The flash clears after 3000ms to match the CSS animation duration in Task 4.

- [ ] **Step 1: Write the failing test**

Create `ui/composables/use-flash-on-change.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useFlashOnChange } from './use-flash-on-change'

describe('useFlashOnChange', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should dont flash before the value has arrived', async () => {
    const value = ref<number | null>(null)
    const flash = useFlashOnChange(value)
    value.value = 100
    await nextTick()
    expect(flash.value).toBe('')
  })

  it('should flash an increase when the value rises', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 101
    await nextTick()
    expect(flash.value).toBe('value-increase')
  })

  it('should flash a decrease when the value falls', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 99
    await nextTick()
    expect(flash.value).toBe('value-decrease')
  })

  it('should dont flash when the change is below the threshold', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 100.0001
    await nextTick()
    expect(flash.value).toBe('')
  })

  it('should respect a custom threshold for small percentage moves', async () => {
    const value = ref<number | null>(0.15)
    const flash = useFlashOnChange(value, 0.00001)
    value.value = 0.1502
    await nextTick()
    expect(flash.value).toBe('value-increase')
  })

  it('should clear the flash after the animation finishes', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 200
    await nextTick()
    vi.advanceTimersByTime(3000)
    expect(flash.value).toBe('')
  })

  it('should keep the flash while the animation is still running', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 200
    await nextTick()
    vi.advanceTimersByTime(2999)
    expect(flash.value).toBe('value-increase')
  })

  it('should replace an increase with a decrease when the value reverses mid-flash', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 200
    await nextTick()
    vi.advanceTimersByTime(1000)
    value.value = 150
    await nextTick()
    vi.advanceTimersByTime(2500)
    expect(flash.value).toBe('value-decrease')
  })
})
```

The last test is the one that catches a naive implementation: a second change must cancel the first change's pending timer, otherwise the older timer fires at t=3000 and wipes a flash that should still have 500ms left.

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --run use-flash-on-change`
Expected: FAIL — cannot resolve `./use-flash-on-change`.

- [ ] **Step 3: Write the implementation**

Create `ui/composables/use-flash-on-change.ts`:

```typescript
import { ref, watch, type Ref } from 'vue'

const FLASH_DURATION = 3000
const DEFAULT_THRESHOLD = 0.001

export function useFlashOnChange(
  value: Ref<number | null | undefined>,
  threshold: number = DEFAULT_THRESHOLD
): Ref<string> {
  const flashClass = ref('')
  let previous = value.value ?? null
  let timeoutId: ReturnType<typeof setTimeout> | null = null

  watch(value, newValue => {
    if (newValue === null || newValue === undefined) return

    if (previous === null) {
      previous = newValue
      return
    }

    const delta = newValue - previous
    previous = newValue
    if (Math.abs(delta) <= threshold) return

    flashClass.value = delta > 0 ? 'value-increase' : 'value-decrease'

    if (timeoutId !== null) clearTimeout(timeoutId)
    timeoutId = setTimeout(() => {
      flashClass.value = ''
      timeoutId = null
    }, FLASH_DURATION)
  })

  return flashClass
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --run use-flash-on-change`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add ui/composables/use-flash-on-change.ts ui/composables/use-flash-on-change.test.ts
git commit -m "Add flash-on-change composable"
```

---

### Task 4: Promote the flash styles to global CSS

**Files:**

- Modify: `ui/styles/base.css` (insert after the `hr` rule that ends at line 95, before the first `@media` block at line 97)
- Modify: `ui/components/instruments/instrument-table.vue:690-731` (delete the moved rules from the scoped `<style>` block)

**Interfaces:**

- Consumes: CSS custom properties `--color-gain-wash` and `--color-loss-wash`, already defined in the theme.
- Produces: global `.value-increase` / `.value-decrease` classes, used by Tasks 6 and 7 and by the existing instruments table.

**Background the implementer needs:** these rules live in `instrument-table.vue`'s _scoped_ style block today, so nothing outside that component can use them. `base.css` already refers to both class names in its `prefers-reduced-motion` block, so the global name is the one already assumed to exist. Moving them is a dedup, not a new pattern. Order does not matter against the reduced-motion override because that override uses `!important`, but insert before the media queries anyway to keep the file readable.

Note for anyone testing this by hand: with macOS "Reduce Motion" enabled the flash is suppressed to a 3s no-op wash and the roll still runs, so the numbers will change without any colour. That is the environment, not a bug.

- [ ] **Step 1: Add the rules to base.css**

Insert into `ui/styles/base.css` immediately after the `hr { ... }` rule and before `@media (max-width: 768px)`:

```css
@keyframes pulse-increase {
  0% {
    background-color: transparent;
  }

  50% {
    background-color: var(--color-gain-wash);
  }

  100% {
    background-color: transparent;
  }
}

@keyframes pulse-decrease {
  0% {
    background-color: transparent;
  }

  50% {
    background-color: var(--color-loss-wash);
  }

  100% {
    background-color: transparent;
  }
}

.value-increase {
  animation: pulse-increase 3s ease-in-out;
  transition: background-color 3s ease-in-out;
}

.value-decrease {
  animation: pulse-decrease 3s ease-in-out;
  transition: background-color 3s ease-in-out;
}
```

- [ ] **Step 2: Delete the duplicates from the instruments table**

In `ui/components/instruments/instrument-table.vue`, delete the `@keyframes pulse-increase`, `@keyframes pulse-decrease`, `.value-increase`, and `.value-decrease` blocks from the scoped `<style>` block. Delete nothing else. The block ends the file, so the file must still end with `</style>`.

- [ ] **Step 3: Verify the rules exist exactly once**

Run: `grep -rn "pulse-increase\|pulse-decrease" ui/`
Expected: matches only in `ui/styles/base.css` — two `@keyframes` definitions and two `animation:` references, four lines total.

- [ ] **Step 4: Run the instruments tests and the linter**

Run: `npm test -- --run instrument-table && npm run lint-format`
Expected: PASS. `lint-format` rewrites `ui/models/generated/domain-models.ts` as a known side effect; revert that file if it shows up as modified.

- [ ] **Step 5: Commit**

```bash
git checkout -- ui/models/generated/domain-models.ts 2>/dev/null || true
git add ui/styles/base.css ui/components/instruments/instrument-table.vue
git commit -m "Move value flash styles to global stylesheet"
```

---

### Task 5: Poll the live summary queries every 5 seconds

**Files:**

- Modify: `ui/constants/api.ts:21-25`
- Modify: `ui/composables/use-portfolio-summary-query.ts:55-77`
- Test: `ui/composables/use-portfolio-summary-query.test.ts` (existing — run it, do not rewrite it)

**Interfaces:**

- Consumes: `REFETCH_INTERVALS` from `../constants/api`, already imported by consumers.
- Produces: `REFETCH_INTERVALS.SUMMARY = 5000`.

**Background the implementer needs:** four queries live in this composable and only two get the interval.

- `current` — yes. This is the live payload; it feeds the headline and today's row.
- `range-change` — yes. This is the `+€X (+Y%)` header, derived from the same live value.
- `historical` — **no.** It is a `useInfiniteQuery`; a refetch re-requests _every page the user has scrolled through_.
- `series` — **no.** It backs the chart and would force a re-render every 5 seconds. It is already merged with `current` by `mergeHistoricalWithCurrent`, so the chart's final point updates for free.

- [ ] **Step 1: Add the constant**

In `ui/constants/api.ts`, add one entry to `REFETCH_INTERVALS`, keeping the existing entries:

```typescript
export const REFETCH_INTERVALS = {
  INSTRUMENTS: 2000,
  SUMMARY: 5000,
  DIVERSIFICATION_ETFS: 60 * 60 * 1000,
  PLATFORMS: 60 * 60 * 1000,
} as const
```

- [ ] **Step 2: Import the constant in the query composable**

Add to the imports at the top of `ui/composables/use-portfolio-summary-query.ts`:

```typescript
import { REFETCH_INTERVALS } from '../constants/api'
```

- [ ] **Step 3: Add the interval to exactly two queries**

In the `currentSummary` query, add `refetchInterval` after `enabled`:

```typescript
const { data: currentSummary, isLoading: isLoadingCurrent } = useQuery({
  queryKey: ['portfolio-summary', 'current', platformsKey],
  queryFn: () => portfolioSummaryService.getCurrent(activePlatforms.value),
  enabled: isAuthenticated,
  refetchInterval: REFETCH_INTERVALS.SUMMARY,
})
```

In the `rangeChange` query, likewise:

```typescript
const { data: rangeChange, error: rangeChangeError } = useQuery({
  queryKey: ['portfolio-summary', 'range-change', platformsKey, rangeKey],
  queryFn: () => portfolioSummaryService.getRangeChange(rangeKey.value, activePlatforms.value),
  placeholderData: keepPreviousData,
  enabled: isAuthenticated,
  refetchInterval: REFETCH_INTERVALS.SUMMARY,
})
```

Leave the `historical` and `series` queries untouched.

- [ ] **Step 4: Verify only two queries poll**

Run: `grep -n "refetchInterval" ui/composables/use-portfolio-summary-query.ts`
Expected: exactly two lines.

- [ ] **Step 5: Run the tests**

Run: `npm test -- --run use-portfolio-summary-query`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add ui/constants/api.ts ui/composables/use-portfolio-summary-query.ts
git commit -m "Poll live summary queries every five seconds"
```

---

### Task 6: Animate the range-change header

**Files:**

- Modify: `ui/components/portfolio/range-change-header.vue` (whole file)
- Test: `ui/components/portfolio/range-change-header.test.ts` (existing — extend, keep all four existing tests passing unchanged)

**Interfaces:**

- Consumes: `useNumberTransition` from `../../composables/use-number-transition`, `useFlashOnChange` from Task 3.
- Produces: nothing consumed by later tasks.

**Background the implementer needs:** this component's existing tests assert exact rendered text on mount and assert that a flat range has classes exactly `['range-change']`. Both keep passing because `useNumberTransition` seeds its display ref with the current value and only animates on _change_, and because `useFlashOnChange` returns `''` until a change arrives — and Vue drops empty strings from a class binding.

`props.amount` is a prop, not a ref, so it must be wrapped before either composable can watch it. Wrap it with `computed(() => props.amount)`, **not** `toRef(props, 'amount')`: `toRef` hands back a _writable_ `Ref<number>`, and a writable ref's setter makes the type invariant, so it will not typecheck against `useNumberTransition`'s `Ref<number | null | undefined>` parameter under strict mode. `computed` yields a read-only `ComputedRef`, which is the pattern `instrument-table.vue` already uses everywhere it feeds a prop into this composable.

Both numbers roll, but only the `amount` drives the flash — they always move together, and two independently-timed washes on one line would flicker. The amount is a currency figure, so the default `0.001` threshold is right and no custom threshold is passed.

- [ ] **Step 1: Write the failing tests**

Append these two tests inside the existing `describe('RangeChangeHeader')` block in `ui/components/portfolio/range-change-header.test.ts`, and add `nextTick` to the existing `vue` import (`import { nextTick } from 'vue'`):

```typescript
it('should flash a gain when the amount rises', async () => {
  const wrapper = mount(RangeChangeHeader, {
    props: { amount: 100, percent: 1 },
  })

  await wrapper.setProps({ amount: 200, percent: 2 })
  await nextTick()

  expect(wrapper.find('.range-change').classes()).toContain('value-increase')
})

it('should flash a loss when the amount falls', async () => {
  const wrapper = mount(RangeChangeHeader, {
    props: { amount: 100, percent: 1 },
  })

  await wrapper.setProps({ amount: 50, percent: 0.5 })
  await nextTick()

  expect(wrapper.find('.range-change').classes()).toContain('value-decrease')
})
```

- [ ] **Step 2: Run the tests to verify the new ones fail**

Run: `npm test -- --run range-change-header`
Expected: the four existing tests PASS, the two new tests FAIL (no `value-increase` class).

- [ ] **Step 3: Rewrite the component**

Replace the `<template>` and `<script setup>` of `ui/components/portfolio/range-change-header.vue`, leaving the `<style>` block exactly as it is:

```vue
<template>
  <div class="range-change" :class="[changeClass, flashClass]">{{ label }}</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useNumberTransition } from '../../composables/use-number-transition'
import { useFlashOnChange } from '../../composables/use-flash-on-change'
import { formatSignedCurrency, formatSignedPercent, getGainLossClass } from '../../utils/formatters'

const props = defineProps<{
  amount: number
  percent: number
}>()

const amount = computed(() => props.amount)
const percent = computed(() => props.percent)

const animatedAmount = useNumberTransition(amount)
const animatedPercent = useNumberTransition(percent)
const flashClass = useFlashOnChange(amount)

const label = computed(
  () =>
    `${formatSignedCurrency(animatedAmount.value, 'EUR')} (${formatSignedPercent(animatedPercent.value)})`
)

const changeClass = computed(() => getGainLossClass(props.amount))
</script>
```

Note `changeClass` deliberately reads `props.amount`, not the animated value: the gain/loss text colour should switch immediately with the real sign rather than at whatever moment the roll happens to cross zero.

- [ ] **Step 4: Run the tests to verify all six pass**

Run: `npm test -- --run range-change-header`
Expected: PASS, 6 tests. If a text assertion now fails with a partially-rolled number, `useNumberTransition` was not seeded correctly — re-read it before changing any test.

- [ ] **Step 5: Commit**

```bash
git add ui/components/portfolio/range-change-header.vue ui/components/portfolio/range-change-header.test.ts
git commit -m "Animate range change header values"
```

---

### Task 7: Animate the headline total

**Files:**

- Modify: `ui/components/portfolio-summary.vue:55-65` (template) and its `<script setup>` imports plus the `latestSummary` area near line 208
- Test: `ui/components/portfolio-summary.test.ts` (existing — run it, extend only if it already mounts the component)

**Interfaces:**

- Consumes: `useNumberTransition`, `useFlashOnChange` (Task 3), the global flash classes (Task 4), the 5s poll (Task 5).
- Produces: the finished feature.

**Background the implementer needs:** the headline is an `<h1>` that spans the container width. Putting the flash class on the `h1` would wash the entire line's width in colour; the class goes on an inner `<span>` so the wash hugs the digits, which is how `instrument-table.vue` does it (`<span :class="getTotalsChangeClass('totalValue')">`).

`latestSummary` is null while loading, so the value passed to the composables must be a computed that yields `number | null`. Both composables must be called at the top level of `<script setup>`, never inside the `v-if` — a computed handles the null window correctly.

Expect a count-up from zero on first paint: `useNumberTransition` animates from 0 when the previous value was null. That matches the instruments table and is intended. There is no green flash on that first paint, because `useFlashOnChange` skips the first arrival.

- [ ] **Step 1: Add the imports**

In the `<script lang="ts" setup>` block of `ui/components/portfolio-summary.vue`, add alongside the other composable imports:

```typescript
import { useNumberTransition } from '../composables/use-number-transition'
import { useFlashOnChange } from '../composables/use-flash-on-change'
```

- [ ] **Step 2: Derive the animated headline value**

Directly beneath the existing `const latestSummary = computed(...)` line, add:

```typescript
const headlineValue = computed(() => latestSummary.value?.totalValue ?? null)
const animatedHeadlineValue = useNumberTransition(headlineValue)
const headlineFlashClass = useFlashOnChange(headlineValue)
```

- [ ] **Step 3: Wrap the headline number in a span**

Change the headline block in the template from:

```html
<h1>{{ formatCurrencyWithSymbol(latestSummary.totalValue) }}</h1>
```

to:

```html
<h1>
  <span :class="headlineFlashClass">{{ formatCurrencyWithSymbol(animatedHeadlineValue) }}</span>
</h1>
```

- [ ] **Step 4: Run the summary tests and the full UI suite**

Run: `npm test -- --run portfolio-summary`
Expected: PASS. If an assertion matched the headline's exact text on mount and now sees a mid-roll number, the fix is to await the animation or assert on the final value — do not remove the animation.

- [ ] **Step 5: Run the whole frontend gate**

Run: `npm run lint-format && npm test -- --run`
Expected: PASS. Revert `ui/models/generated/domain-models.ts` if `lint-format` rewrote it.

- [ ] **Step 6: Commit**

```bash
git checkout -- ui/models/generated/domain-models.ts 2>/dev/null || true
git add ui/components/portfolio-summary.vue
git commit -m "Animate portfolio headline total"
```

---

### Task 8: Full verification

**Files:** none modified.

**Interfaces:** none.

- [ ] **Step 1: Run the backend suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 2: Run the real lint gate**

Run: `./gradlew ktlintCheck detekt`
Expected: PASS. Note `detektMain`/`detektTest` are _not_ part of the CI gate and report pre-existing type-resolution findings; do not chase those.

- [ ] **Step 3: Run the frontend suite**

Run: `npm run lint-format && npm test -- --run`
Expected: PASS.

- [ ] **Step 4: Check for unused exports**

Run: `npm run check-unused`
Expected: no new findings for `use-flash-on-change`. Untracked worktrees under `.claude/worktrees/` can make knip fail locally in ways CI never sees; ignore findings from those paths.

- [ ] **Step 5: Confirm the poll and the warm job by hand**

Start the stack with `npm run dev`, open the Summary tab, and open the browser network panel.

Expected: a request to `/api/portfolio-summary/current?platforms=...` every 5 seconds, each returning in well under a second (not 16-18s). Watch for two minutes: when the backend job turns the cache over, the headline should roll to its new value and wash green or red.

If every response is fast but no value ever changes, the prices themselves have not moved — check the backend log for `CurrentDaySummaryRefreshJob` activity rather than assuming the frontend is broken.

- [ ] **Step 6: Commit any stragglers**

```bash
git status
```

Expected: clean, apart from `docs/superpowers/plans/` if it is untracked.
