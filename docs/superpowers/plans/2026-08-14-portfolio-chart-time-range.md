# Portfolio chart time-range selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `1W · 1M · 6M · YTD · 1Y · 2Y · 3Y · MAX` chip row under the Portfolio Summary chart, where each chip issues a real backend request for that window.

**Architecture:** A new `SummaryRange` enum owns the calendar math and a 366-point sampling cap, so no range costs more than 1Y (~2.5 s cold) despite MAX spanning 2,416 days. A new `GET /api/portfolio-summary/series` endpoint returns a flat `List<PortfolioSummaryDto>` — separate from `/historical`, so the table's paginated infinite-scroll path cannot regress. The frontend adds one `useQuery` keyed on `(platforms, range)` with `keepPreviousData` so a cold range switch does not blank the chart.

**Tech Stack:** Kotlin 2.3 / Spring Boot 4.0 / Redis (`PLATFORM_SUMMARY_CACHE`, 1 h TTL); Vue 3.5 / TypeScript / `@tanstack/vue-query` / `@vueuse/core` `useLocalStorage`; Atrium + JUnit 5 + MockMvc backend tests, Vitest + Vue Test Utils frontend tests, Playwright visual regression.

**Spec:** `docs/superpowers/specs/2026-08-14-portfolio-chart-time-range-design.md`

## Global Constraints

- Branch is `feature/portfolio-chart-time-range` (already exists, already holds the spec commit `7c877784`). Never commit to `main`.
- Commit subjects: uppercase imperative verb, max 50 chars, **no** `feat:`/`fix:`/`chore:` prefixes, no body. **Never** add "Generated with Claude Code", "Co-Authored-By: Claude", or any AI attribution.
- **No comments in code.** Not in Kotlin, not in TypeScript, not in Vue. Only TypeScript triple-slash directives (`///`) are exempt.
- Kotlin: no blank lines inside method bodies; `val` over `var`; guard clauses over nesting; `runCatching` over try/catch; data classes in their own files (ArchUnit-enforced); controllers are 1–2 line delegations; caching lives in a separate service (Spring proxy self-invocation); files under 300 lines.
- Kotlin error/log messages: single sentence, no trailing period.
- Kotlin tests: Atrium (`import ch.tutteli.atrium.api.fluent.en_GB.*`, `import ch.tutteli.atrium.api.verbs.expect`), backtick full-sentence names, "cannot"/"dont" spelled without apostrophes, the assertion is the last statement, one behaviour per test.
- BigDecimal assertions use `toEqualNumerically()`, never `toEqual()`.
- Frontend: TypeScript strict, Composition API, `const` by default, no `any`, components under 200 lines, `useLocalStorage` from `@vueuse/core` — never raw `localStorage`.
- After any UI change run **both** `npm run lint-format` and `npm test` from `ui/`.
- `npm run lint-format` rewrites `ui/models/generated/domain-models.ts` (strips its eslint-disable header). That diff is churn — `git checkout ui/models/generated/domain-models.ts` before committing, never stage it.
- `npm run lint-format` also exits 1 locally if `.claude/worktrees/` exists (knip scans untracked worktree copies). Judge knip output on `ui/**` paths only.

## Deviation from the spec

The spec's `build.gradle.kts` step — adding `"ee.tenman.portfolio.domain.SummaryRange"` to the typescript-generator `classes` list — is **dropped**. The spec flagged this as conditional ("if the generator emits `ONE_YEAR = 'ONE_YEAR'` rather than honouring `@JsonValue`…"), and all five enums already in that list (`Currency`, `Platform`, `ProviderName`, `TransactionType`, `PriceChangePeriod`) emit `NAME = "NAME"`. So the generated enum would carry neither the wire value (`1Y`) nor the chip label (`1Y`), and the frontend would need a hand-written 8-entry label map regardless. Instead the frontend declares the codes once in `ui/models/chart-range.ts`, where value === wire format === label and no mapping code exists. `@get:JsonValue` is dropped with it — `SummaryRange` never appears in a response body.

Everything else follows the spec as written.

## File Structure

**Backend — create**

| File                                                                                   | Responsibility                                                                           |
| -------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `src/main/kotlin/ee/tenman/portfolio/domain/SummaryRange.kt`                           | The 8 ranges, their calendar math, the 366-point sampling cap. Pure, no Spring.          |
| `src/main/kotlin/ee/tenman/portfolio/configuration/SummaryRangeConverter.kt`           | Binds the `?range=1Y` query param string to the enum.                                    |
| `src/main/kotlin/ee/tenman/portfolio/service/summary/PortfolioSummaryDtoMapper.kt`     | The single entity → DTO mapping, extracted from the controller so both callers share it. |
| `src/main/kotlin/ee/tenman/portfolio/service/summary/PortfolioSummarySeriesService.kt` | Picks the filtered vs unfiltered path. Keeps the controller thin.                        |
| `src/test/kotlin/ee/tenman/portfolio/domain/SummaryRangeTest.kt`                       | Unit tests for the enum.                                                                 |

**Backend — modify**

| File                                                             | Change                                                                                               |
| ---------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `service/summary/SummaryService.kt`                              | `+ getSeriesForPlatforms(platforms, range)`, `+ getSeries(range)`                                    |
| `service/summary/PlatformSummaryCacheService.kt`                 | `+ @Cacheable getSeriesForPlatforms(platforms, range)`                                               |
| `controller/PortfolioSummaryController.kt`                       | `+ GET /series`; private `toDto(profitChange24h)` and `DAYS_PER_MONTH` move out to the shared mapper |
| `configuration/PortfolioSummaryWarmup.kt`                        | two new array entries                                                                                |
| `src/test/kotlin/.../controller/PortfolioSummaryControllerIT.kt` | `/series` integration coverage                                                                       |

**Frontend — create**

| File                                                 | Responsibility                                                                                  |
| ---------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `ui/models/chart-range.ts`                           | `CHART_RANGES`, `ChartRange`, `DEFAULT_CHART_RANGE`. Single frontend source for the vocabulary. |
| `ui/composables/use-chart-range.ts`                  | Persisted selection + stale-value self-heal.                                                    |
| `ui/composables/use-chart-range.test.ts`             |                                                                                                 |
| `ui/components/portfolio/chart-range-filter.vue`     | Presentational chip row. No fetching.                                                           |
| `ui/components/portfolio/chart-range-filter.test.ts` |                                                                                                 |

**Frontend — modify**

| File                                                 | Change                                               |
| ---------------------------------------------------- | ---------------------------------------------------- |
| `ui/constants/api.ts`                                | `+ PORTFOLIO_SUMMARY_SERIES`                         |
| `ui/constants/storage-keys.ts`                       | `+ SUMMARY_CHART_RANGE`                              |
| `ui/services/portfolio-summary-service.ts`           | `+ getSeries`                                        |
| `ui/composables/use-portfolio-summary-query.ts`      | `+ series query`, `+ chartSummaries`                 |
| `ui/composables/use-portfolio-summary-query.test.ts` | mock `getSeries`, cover the new query                |
| `ui/components/portfolio-summary.vue`                | render the chips, feed `chartSummaries` to the chart |
| `ui/tests/visual/summary-fixture.ts`                 | `+ /series` route stub                               |

---

### Task 1: `SummaryRange` enum

The whole feature's calendar and resolution logic, with no Spring or database in the way. Everything downstream consumes it.

**Files:**

- Create: `src/main/kotlin/ee/tenman/portfolio/domain/SummaryRange.kt`
- Test: `src/test/kotlin/ee/tenman/portfolio/domain/SummaryRangeTest.kt`

**Interfaces:**

- Consumes: nothing.
- Produces:
  - `enum class SummaryRange(val code: String)` with constants, in this order: `ONE_WEEK("1W")`, `ONE_MONTH("1M")`, `SIX_MONTHS("6M")`, `YTD("YTD")`, `ONE_YEAR("1Y")`, `TWO_YEARS("2Y")`, `THREE_YEARS("3Y")`, `MAX("MAX")`
  - `fun startDate(today: LocalDate): LocalDate?` — null means unbounded (`MAX` only)
  - `fun dates(firstDate: LocalDate, today: LocalDate): List<LocalDate>` — ascending, sampled, ends at `today.minusDays(1)`
  - `companion object { const val MAX_POINTS = 366 }`
  - `companion object { fun from(code: String): SummaryRange }` — case-insensitive on `code`, throws `IllegalArgumentException` otherwise
  - `companion object { fun <T> sample(items: List<T>, maxPoints: Int = MAX_POINTS): List<T> }`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/ee/tenman/portfolio/domain/SummaryRangeTest.kt`:

```kotlin
package ee.tenman.portfolio.domain

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SummaryRangeTest {
  private val today = LocalDate.of(2026, 8, 14)

  @Test
  fun `should start one week before today for the one week range`() {
    expect(SummaryRange.ONE_WEEK.startDate(today)).toEqual(LocalDate.of(2026, 8, 7))
  }

  @Test
  fun `should start six calendar months before today for the six months range`() {
    expect(SummaryRange.SIX_MONTHS.startDate(today)).toEqual(LocalDate.of(2026, 2, 14))
  }

  @Test
  fun `should start on the first day of the year for the year to date range`() {
    expect(SummaryRange.YTD.startDate(today)).toEqual(LocalDate.of(2026, 1, 1))
  }

  @Test
  fun `should start on the first of January when today is the last day of the year`() {
    expect(SummaryRange.YTD.startDate(LocalDate.of(2026, 12, 31))).toEqual(LocalDate.of(2026, 1, 1))
  }

  @Test
  fun `should start on the twenty eighth of February when a leap day is one year back`() {
    expect(SummaryRange.ONE_YEAR.startDate(LocalDate.of(2025, 2, 28))).toEqual(LocalDate.of(2024, 2, 28))
  }

  @Test
  fun `should report an unbounded start for the max range`() {
    expect(SummaryRange.MAX.startDate(today)).toEqual(null)
  }

  @Test
  fun `should produce seven consecutive dates ending yesterday for the one week range`() {
    expect(SummaryRange.ONE_WEEK.dates(LocalDate.of(2020, 1, 2), today)).toContainExactly(
      LocalDate.of(2026, 8, 7),
      LocalDate.of(2026, 8, 8),
      LocalDate.of(2026, 8, 9),
      LocalDate.of(2026, 8, 10),
      LocalDate.of(2026, 8, 11),
      LocalDate.of(2026, 8, 12),
      LocalDate.of(2026, 8, 13),
    )
  }

  @Test
  fun `should start from the first transaction date when it falls inside the window`() {
    expect(SummaryRange.ONE_WEEK.dates(LocalDate.of(2026, 8, 11), today)).toContainExactly(
      LocalDate.of(2026, 8, 11),
      LocalDate.of(2026, 8, 12),
      LocalDate.of(2026, 8, 13),
    )
  }

  @Test
  fun `should produce one hundred and eighty one dates for the six months range`() {
    expect(SummaryRange.SIX_MONTHS.dates(LocalDate.of(2020, 1, 2), today)).toHaveSize(181)
  }

  @Test
  fun `should cap the max range at the sampling limit`() {
    expect(SummaryRange.MAX.dates(LocalDate.of(2020, 1, 2), today)).toHaveSize(366)
  }

  @Test
  fun `should keep both endpoints when sampling caps the max range`() {
    val dates = SummaryRange.MAX.dates(LocalDate.of(2020, 1, 2), today)
    expect(dates.first() to dates.last()).toEqual(LocalDate.of(2020, 1, 2) to LocalDate.of(2026, 8, 13))
  }

  @Test
  fun `should return dates in ascending order for the max range`() {
    val dates = SummaryRange.MAX.dates(LocalDate.of(2020, 1, 2), today)
    expect(dates).toEqual(dates.sorted())
  }

  @Test
  fun `should return no dates when the first transaction is after yesterday`() {
    expect(SummaryRange.MAX.dates(today, today)).toHaveSize(0)
  }

  @Test
  fun `should return the list unchanged when it is at or under the sampling limit`() {
    expect(SummaryRange.sample(listOf("ä", "ö", "ü"), 3)).toContainExactly("ä", "ö", "ü")
  }

  @Test
  fun `should keep the first and last element when sampling shrinks the list`() {
    expect(SummaryRange.sample((1..100).toList(), 5)).toContainExactly(1, 26, 51, 75, 100)
  }

  @Test
  fun `should keep every sampled index in bounds at the production limit`() {
    expect(SummaryRange.sample((1..367).toList())).toHaveSize(366)
  }

  @Test
  fun `should resolve a range from its lowercase code`() {
    expect(SummaryRange.from("ytd")).toEqual(SummaryRange.YTD)
  }

  @Test
  fun `should resolve a range from its uppercase code`() {
    expect(SummaryRange.from("1Y")).toEqual(SummaryRange.ONE_YEAR)
  }

  @Test
  fun `should throw when the code is unknown`() {
    expect { SummaryRange.from("1D") }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should declare the ranges in chip order`() {
    expect(SummaryRange.entries.map { it.code })
      .toContainExactly("1W", "1M", "6M", "YTD", "1Y", "2Y", "3Y", "MAX")
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests "ee.tenman.portfolio.domain.SummaryRangeTest"
```

Expected: compilation failure — `Unresolved reference: SummaryRange`.

- [ ] **Step 3: Write the implementation**

Create `src/main/kotlin/ee/tenman/portfolio/domain/SummaryRange.kt`:

```kotlin
package ee.tenman.portfolio.domain

import java.time.LocalDate
import kotlin.math.roundToInt

enum class SummaryRange(
  val code: String,
) {
  ONE_WEEK("1W"),
  ONE_MONTH("1M"),
  SIX_MONTHS("6M"),
  YTD("YTD"),
  ONE_YEAR("1Y"),
  TWO_YEARS("2Y"),
  THREE_YEARS("3Y"),
  MAX("MAX"),
  ;

  fun startDate(today: LocalDate): LocalDate? =
    when (this) {
      ONE_WEEK -> today.minusWeeks(1)
      ONE_MONTH -> today.minusMonths(1)
      SIX_MONTHS -> today.minusMonths(6)
      YTD -> today.withDayOfYear(1)
      ONE_YEAR -> today.minusYears(1)
      TWO_YEARS -> today.minusYears(2)
      THREE_YEARS -> today.minusYears(3)
      MAX -> null
    }

  fun dates(
    firstDate: LocalDate,
    today: LocalDate,
  ): List<LocalDate> {
    val end = today.minusDays(1)
    if (firstDate.isAfter(end)) return emptyList()
    val start = startDate(today)?.coerceAtLeast(firstDate) ?: firstDate
    if (start.isAfter(end)) return emptyList()
    val all = generateSequence(start) { d -> d.plusDays(1).takeIf { !it.isAfter(end) } }.toList()
    return sample(all)
  }

  companion object {
    const val MAX_POINTS = 366

    fun from(code: String): SummaryRange =
      entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown summary range $code")

    fun <T> sample(
      items: List<T>,
      maxPoints: Int = MAX_POINTS,
    ): List<T> {
      if (items.size <= maxPoints) return items
      val step = (items.size - 1).toDouble() / (maxPoints - 1)
      return (0 until maxPoints).map { items[(it * step).roundToInt()] }
    }
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew test --tests "ee.tenman.portfolio.domain.SummaryRangeTest"
```

Expected: PASS, 19 tests.

- [ ] **Step 5: Verify the enum does not break the architecture rules**

```bash
./gradlew test --tests "ee.tenman.portfolio.ArchitectureTest"
```

Expected: PASS. `domain` is an existing layer; this adds no new package.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/domain/SummaryRange.kt src/test/kotlin/ee/tenman/portfolio/domain/SummaryRangeTest.kt
git commit -m "Add SummaryRange enum for chart windows"
```

---

### Task 2: Query-parameter binding for `SummaryRange`

Spring binds enums by constant name (`SIX_MONTHS`) out of the box, but the wire format is the code (`6M`). A `Converter` bean bridges the two; Spring Boot auto-registers `Converter` beans into the MVC conversion service.

**Files:**

- Create: `src/main/kotlin/ee/tenman/portfolio/configuration/SummaryRangeConverter.kt`
- Test: `src/test/kotlin/ee/tenman/portfolio/configuration/SummaryRangeConverterTest.kt`

**Interfaces:**

- Consumes: `SummaryRange.from(code: String): SummaryRange` from Task 1.
- Produces: `class SummaryRangeConverter : Converter<String, SummaryRange>` — a `@Component`, so `@RequestParam range: SummaryRange` accepts `1W`…`MAX`.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/ee/tenman/portfolio/configuration/SummaryRangeConverterTest.kt`:

```kotlin
package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.SummaryRange
import org.junit.jupiter.api.Test

class SummaryRangeConverterTest {
  private val converter = SummaryRangeConverter()

  @Test
  fun `should convert a range code into its enum constant`() {
    expect(converter.convert("6M")).toEqual(SummaryRange.SIX_MONTHS)
  }

  @Test
  fun `should throw when the range code is unknown`() {
    expect { converter.convert("42Y") }.toThrow<IllegalArgumentException>()
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests "ee.tenman.portfolio.configuration.SummaryRangeConverterTest"
```

Expected: compilation failure — `Unresolved reference: SummaryRangeConverter`.

- [ ] **Step 3: Write the implementation**

Create `src/main/kotlin/ee/tenman/portfolio/configuration/SummaryRangeConverter.kt`:

```kotlin
package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.domain.SummaryRange
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
class SummaryRangeConverter : Converter<String, SummaryRange> {
  override fun convert(source: String): SummaryRange = SummaryRange.from(source)
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew test --tests "ee.tenman.portfolio.configuration.SummaryRangeConverterTest"
```

Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/configuration/SummaryRangeConverter.kt src/test/kotlin/ee/tenman/portfolio/configuration/SummaryRangeConverterTest.kt
git commit -m "Bind range query parameter to SummaryRange"
```

---

### Task 3: Series computation in `SummaryService`

Two paths. Platform-filtered recomputes per date from the transaction list (~3.4 ms/date, hence the cap). Unfiltered reads the already-materialised `portfolio_daily_summary` rows, which is ~0.07 s for the full history, and samples the rows actually found rather than the dates requested — so gaps in stored history yield fewer points instead of missing lookups.

**Files:**

- Modify: `src/main/kotlin/ee/tenman/portfolio/service/summary/SummaryService.kt` (add two methods; the file is 239 lines and stays under 300)
- Test: `src/test/kotlin/ee/tenman/portfolio/service/summary/SummaryServiceSeriesTest.kt`

**Interfaces:**

- Consumes: `SummaryRange.dates`, `SummaryRange.startDate`, `SummaryRange.sample` from Task 1. Existing private members of `SummaryService`: `transactionSortOrder`, `calculateSummariesForDates(sortedDates, sortedTransactions)`, injected `clock`, `transactionService`, `portfolioDailySummaryRepository`.
- Produces:
  - `fun getSeriesForPlatforms(platforms: List<Platform>, range: SummaryRange): List<PortfolioDailySummary>`
  - `fun getSeries(range: SummaryRange): List<PortfolioDailySummary>`

  Both return summaries ascending by `entryDate`, ending no later than yesterday, at most `SummaryRange.MAX_POINTS` long, empty when there is nothing to return.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/ee/tenman/portfolio/service/summary/SummaryServiceSeriesTest.kt`. It uses MockK (not Mockito) and a fixed `Clock`, so it needs no containers.

```kotlin
package ee.tenman.portfolio.service.summary

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.SummaryRange
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class SummaryServiceSeriesTest {
  private val today = LocalDate.of(2026, 8, 14)
  private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
  private val repository = mockk<PortfolioDailySummaryRepository>()
  private val transactionService = mockk<TransactionService>()
  private val batchProcessor = mockk<SummaryBatchProcessorService>()
  private val summaryCacheService = mockk<SummaryCacheService>()

  private val service =
    SummaryService(
      portfolioDailySummaryRepository = repository,
      transactionService = transactionService,
      cacheManager = mockk<CacheManager>(),
      clock = clock,
      summaryBatchProcessor = batchProcessor,
      summaryDeletionService = mockk<SummaryDeletionService>(),
      summaryCacheService = summaryCacheService,
      dailySummaryCalculator = mockk<DailySummaryCalculator>(),
    )

  private fun transaction(date: LocalDate): PortfolioTransaction =
    PortfolioTransaction(
      instrument = Instrument("TÖÖ:TLN", "Tööstus", "ETF", "EUR"),
      transactionType = TransactionType.BUY,
      quantity = BigDecimal.ONE,
      price = BigDecimal.TEN,
      transactionDate = date,
      platform = Platform.LHV,
    )

  private fun summary(date: LocalDate): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal.TEN,
      xirrAnnualReturn = BigDecimal.ZERO,
      realizedProfit = BigDecimal.ZERO,
      unrealizedProfit = BigDecimal.ZERO,
      totalProfit = BigDecimal.ZERO,
      earningsPerDay = BigDecimal.ZERO,
    )

  @Test
  fun `should request one week of dates from the batch processor`() {
    every { transactionService.getAllTransactions(listOf("LHV")) } returns listOf(transaction(LocalDate.of(2020, 1, 2)))
    val dates = slot<List<LocalDate>>()
    every { batchProcessor.calculateSummaries(capture(dates), any()) } returns emptyList()

    service.getSeriesForPlatforms(listOf(Platform.LHV), SummaryRange.ONE_WEEK)

    expect(dates.captured).toHaveSize(7)
  }

  @Test
  fun `should cap the requested dates at the sampling limit for the max range`() {
    every { transactionService.getAllTransactions(listOf("LHV")) } returns listOf(transaction(LocalDate.of(2020, 1, 2)))
    val dates = slot<List<LocalDate>>()
    every { batchProcessor.calculateSummaries(capture(dates), any()) } returns emptyList()

    service.getSeriesForPlatforms(listOf(Platform.LHV), SummaryRange.MAX)

    expect(dates.captured).toHaveSize(366)
  }

  @Test
  fun `should return no filtered series when there are no transactions`() {
    every { transactionService.getAllTransactions(listOf("LHV")) } returns emptyList()

    expect(service.getSeriesForPlatforms(listOf(Platform.LHV), SummaryRange.MAX)).toHaveSize(0)
  }

  @Test
  fun `should read stored rows between the range start and yesterday`() {
    every { repository.findAllByEntryDateBetween(any(), any()) } returns emptyList()

    service.getSeries(SummaryRange.ONE_WEEK)

    verify { repository.findAllByEntryDateBetween(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 13)) }
  }

  @Test
  fun `should read stored rows from the epoch for the max range`() {
    every { repository.findAllByEntryDateBetween(any(), any()) } returns emptyList()

    service.getSeries(SummaryRange.MAX)

    verify { repository.findAllByEntryDateBetween(LocalDate.EPOCH, LocalDate.of(2026, 8, 13)) }
  }

  @Test
  fun `should sample the stored rows down to the limit`() {
    val stored = (0 until 900).map { summary(LocalDate.of(2020, 1, 2).plusDays(it.toLong())) }
    every { repository.findAllByEntryDateBetween(any(), any()) } returns stored

    expect(service.getSeries(SummaryRange.MAX)).toHaveSize(366)
  }

  @Test
  fun `should return stored rows sorted by entry date ascending`() {
    val stored = listOf(summary(LocalDate.of(2026, 8, 12)), summary(LocalDate.of(2026, 8, 10)))
    every { repository.findAllByEntryDateBetween(any(), any()) } returns stored

    expect(service.getSeries(SummaryRange.ONE_WEEK).map { it.entryDate })
      .toEqual(listOf(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)))
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests "ee.tenman.portfolio.service.summary.SummaryServiceSeriesTest"
```

Expected: compilation failure — `Unresolved reference: getSeriesForPlatforms`.

- [ ] **Step 3: Write the implementation**

In `src/main/kotlin/ee/tenman/portfolio/service/summary/SummaryService.kt`, add the import:

```kotlin
import ee.tenman.portfolio.domain.SummaryRange
```

and insert both methods immediately after `getHistoricalSummariesForPlatforms` (which ends at line 99):

```kotlin
  fun getSeriesForPlatforms(
    platforms: List<Platform>,
    range: SummaryRange,
  ): List<PortfolioDailySummary> {
    val sortedTransactions =
      transactionService
        .getAllTransactions(platforms.map { it.name })
        .sortedWith(transactionSortOrder)
    val firstDate = sortedTransactions.firstOrNull()?.transactionDate ?: return emptyList()
    val dates = range.dates(firstDate, LocalDate.now(clock))
    if (dates.isEmpty()) return emptyList()
    return calculateSummariesForDates(dates, sortedTransactions)
  }

  @Transactional(readOnly = true)
  fun getSeries(range: SummaryRange): List<PortfolioDailySummary> {
    val today = LocalDate.now(clock)
    val start = range.startDate(today) ?: LocalDate.EPOCH
    val stored = portfolioDailySummaryRepository.findAllByEntryDateBetween(start, today.minusDays(1))
    return SummaryRange.sample(stored.sortedBy { it.entryDate })
  }
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew test --tests "ee.tenman.portfolio.service.summary.SummaryServiceSeriesTest"
```

Expected: PASS, 7 tests.

- [ ] **Step 5: Verify the existing summary tests still pass**

```bash
./gradlew test --tests "ee.tenman.portfolio.service.summary.*"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/service/summary/SummaryService.kt src/test/kotlin/ee/tenman/portfolio/service/summary/SummaryServiceSeriesTest.kt
git commit -m "Compute portfolio summary series per range"
```

---

### Task 4: Cache the filtered series and expose `/series`

The filtered path is the expensive one, so it gets a `@Cacheable` in the existing separate cache service — never on `SummaryService` itself, or the Spring proxy is bypassed by self-invocation. A dedicated series service owns path selection and DTO mapping so the controller stays a one-liner.

**Files:**

- Modify: `src/main/kotlin/ee/tenman/portfolio/service/summary/PlatformSummaryCacheService.kt`
- Create: `src/main/kotlin/ee/tenman/portfolio/service/summary/PortfolioSummaryDtoMapper.kt`
- Create: `src/main/kotlin/ee/tenman/portfolio/service/summary/PortfolioSummarySeriesService.kt`
- Modify: `src/main/kotlin/ee/tenman/portfolio/controller/PortfolioSummaryController.kt`
- Test: `src/test/kotlin/ee/tenman/portfolio/controller/PortfolioSummaryControllerIT.kt` (append)

**Interfaces:**

- Consumes: `SummaryService.getSeriesForPlatforms` and `SummaryService.getSeries` from Task 3; `SummaryRangeConverter` from Task 2; `PlatformSummaryCacheService.platformKey(platforms)` (already exists); `Platform.parseList(values: List<String>?): List<Platform>?` (already exists — returns `null` when `values` is null or contains nothing recognisable).
- Produces:
  - `fun PortfolioDailySummary.toSummaryDto(profitChange24h: BigDecimal? = null): PortfolioSummaryDto` — top-level in `service.summary`, replacing the controller's private copy
  - `PlatformSummaryCacheService.getSeriesForPlatforms(platforms: List<Platform>, range: SummaryRange): List<PortfolioDailySummary>`
  - `PortfolioSummarySeriesService.getSeries(range: SummaryRange, platforms: List<Platform>?): List<PortfolioSummaryDto>` — `platforms == null` selects the unfiltered stored-row path
  - `GET /api/portfolio-summary/series?range=6M&platforms=LHV&platforms=…` → `List<PortfolioSummaryDto>` with every `totalProfitChange24h` null

- [ ] **Step 1: Write the failing test**

Append to `src/test/kotlin/ee/tenman/portfolio/controller/PortfolioSummaryControllerIT.kt`, inside the existing test class. Reuse whatever fixture setup the surrounding tests already use for seeding instruments, transactions and daily summaries, and the existing `DEFAULT_COOKIE`:

```kotlin
  @Test
  fun `should return the six month series by default`() {
    mockMvc
      .perform(get("/api/portfolio-summary/series").cookie(DEFAULT_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$").isArray)
  }

  @Test
  fun `should return no more than the sampling limit of points for the max range`() {
    mockMvc
      .perform(get("/api/portfolio-summary/series").param("range", "MAX").cookie(DEFAULT_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(lessThanOrEqualTo(366)))
  }

  @Test
  fun `should return a platform filtered series`() {
    mockMvc
      .perform(
        get("/api/portfolio-summary/series")
          .param("range", "1W")
          .param("platforms", "LHV")
          .cookie(DEFAULT_COOKIE),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$").isArray)
  }

  @Test
  fun `should omit the twenty four hour change from every series point`() {
    mockMvc
      .perform(
        get("/api/portfolio-summary/series")
          .param("range", "1W")
          .param("platforms", "LHV")
          .cookie(DEFAULT_COOKIE),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$[*].totalProfitChange24h").value(everyItem(nullValue())))
  }

  @Test
  fun `should reject an unknown range code`() {
    mockMvc
      .perform(get("/api/portfolio-summary/series").param("range", "1D").cookie(DEFAULT_COOKIE))
      .andExpect(status().isBadRequest)
  }
```

Add the Hamcrest imports the file does not already have:

```kotlin
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.hamcrest.Matchers.nullValue
```

If the last test returns 500 rather than 400, the project has no `IllegalArgumentException` → 400 mapping; in that case change the expectation to `status().is4xxClientError` **only after** confirming with `grep -rn "IllegalArgumentException" src/main/kotlin/ee/tenman/portfolio/configuration/` that no `@ControllerAdvice` handles it — Spring wraps converter failures in `MethodArgumentTypeMismatchException`, which resolves to 400 by default.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests "ee.tenman.portfolio.controller.PortfolioSummaryControllerIT"
```

Expected: the five new tests fail with 404 (no `/series` mapping).

- [ ] **Step 3: Add the cached filtered path**

In `src/main/kotlin/ee/tenman/portfolio/service/summary/PlatformSummaryCacheService.kt`, add the import:

```kotlin
import ee.tenman.portfolio.domain.SummaryRange
```

and append this method before `platformKey`:

```kotlin
  @Cacheable(
    value = [PLATFORM_SUMMARY_CACHE],
    key = "'platform-series-' + #root.target.platformKey(#platforms) + '-' + #range.name()",
    unless = "#result.isEmpty()",
  )
  fun getSeriesForPlatforms(
    platforms: List<Platform>,
    range: SummaryRange,
  ): List<PortfolioDailySummary> = summaryService.getSeriesForPlatforms(platforms, range)
```

- [ ] **Step 4: Extract the shared DTO mapper**

The controller currently owns the only entity → DTO mapping, as two private extension overloads plus a companion constant. The series service needs the same mapping with a null 24h change, so extract the leaf overload rather than copying it.

Create `src/main/kotlin/ee/tenman/portfolio/service/summary/PortfolioSummaryDtoMapper.kt`:

```kotlin
package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.PortfolioDailySummary
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import java.math.BigDecimal

private val DAYS_PER_MONTH = BigDecimal(365.25 / 12)

fun PortfolioDailySummary.toSummaryDto(profitChange24h: BigDecimal? = null) =
  PortfolioSummaryDto(
    date = entryDate,
    totalValue = totalValue,
    xirrAnnualReturn = xirrAnnualReturn,
    realizedProfit = realizedProfit,
    unrealizedProfit = unrealizedProfit,
    totalProfit = totalProfit,
    earningsPerDay = earningsPerDay,
    earningsPerMonth = earningsPerDay.multiply(DAYS_PER_MONTH),
    totalProfitChange24h = profitChange24h,
  )
```

In `src/main/kotlin/ee/tenman/portfolio/controller/PortfolioSummaryController.kt`, **delete** the leaf overload and the companion object:

```kotlin
  private fun PortfolioDailySummary.toDto(profitChange24h: BigDecimal?) =
    PortfolioSummaryDto(
      date = entryDate,
      totalValue = totalValue,
      xirrAnnualReturn = xirrAnnualReturn,
      realizedProfit = realizedProfit,
      unrealizedProfit = unrealizedProfit,
      totalProfit = totalProfit,
      earningsPerDay = earningsPerDay,
      earningsPerMonth = earningsPerDay.multiply(DAYS_PER_MONTH),
      totalProfitChange24h = profitChange24h,
    )

  companion object {
    private val DAYS_PER_MONTH = BigDecimal(365.25 / 12)
  }
```

**Keep** the lookup overload, changing only its delegation target:

```kotlin
  private fun PortfolioDailySummary.toDto(lookup: Map<LocalDate, PortfolioDailySummary>): PortfolioSummaryDto {
    val profitChange24h = lookup[entryDate.minusDays(1)]?.let { totalProfit.subtract(it.totalProfit) }
    return toSummaryDto(profitChange24h)
  }
```

Then change the one remaining direct caller, inside `getFilteredCurrentSummary`, from `summary.toDto(profitChange24h)` to `summary.toSummaryDto(profitChange24h)`, and add the import:

```kotlin
import ee.tenman.portfolio.service.summary.toSummaryDto
```

If `BigDecimal` is now unused in the controller's imports, remove it; leave every other import alone.

- [ ] **Step 5: Add the series service**

Create `src/main/kotlin/ee/tenman/portfolio/service/summary/PortfolioSummarySeriesService.kt`:

```kotlin
package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.SummaryRange
import ee.tenman.portfolio.dto.PortfolioSummaryDto
import org.springframework.stereotype.Service

@Service
class PortfolioSummarySeriesService(
  private val summaryService: SummaryService,
  private val platformSummaryCacheService: PlatformSummaryCacheService,
) {
  fun getSeries(
    range: SummaryRange,
    platforms: List<Platform>?,
  ): List<PortfolioSummaryDto> {
    if (platforms == null) return summaryService.getSeries(range).map { it.toSummaryDto() }
    return platformSummaryCacheService.getSeriesForPlatforms(platforms, range).map { it.toSummaryDto() }
  }
}
```

- [ ] **Step 6: Add the endpoint**

In `src/main/kotlin/ee/tenman/portfolio/controller/PortfolioSummaryController.kt`, add the imports:

```kotlin
import ee.tenman.portfolio.domain.SummaryRange
import ee.tenman.portfolio.service.summary.PortfolioSummarySeriesService
```

add the constructor parameter after `annualWindowService`:

```kotlin
  private val seriesService: PortfolioSummarySeriesService,
```

and add the mapping after `getCurrentPortfolioSummary`:

```kotlin
  @GetMapping("/series")
  @Loggable
  fun getSummarySeries(
    @RequestParam(defaultValue = "6M") range: SummaryRange,
    @RequestParam(required = false) platforms: List<String>?,
  ): List<PortfolioSummaryDto> = seriesService.getSeries(range, Platform.parseList(platforms))
```

- [ ] **Step 7: Run the test to verify it passes**

```bash
./gradlew test --tests "ee.tenman.portfolio.controller.PortfolioSummaryControllerIT"
```

Expected: PASS, including the five new tests. The pre-existing tests in this class cover `/current` and `/historical`, which Step 4 rerouted through the extracted mapper — they must stay green, and they are the regression gate for that extraction.

- [ ] **Step 8: Verify architecture and lint gates**

```bash
./gradlew ktlintCheck detekt test --tests "ee.tenman.portfolio.ArchitectureTest"
```

Expected: PASS. `eachSourceFileShouldContainOnlyOneTopLevelClass` counts top-level classes, and a functions-only Kotlin file compiles to a single facade class, so the new mapper file does not violate it. If `serviceMethodsShouldNotHaveTooManyDependencies` fails, the cause is the new controller constructor parameter.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/service/summary/PlatformSummaryCacheService.kt src/main/kotlin/ee/tenman/portfolio/service/summary/PortfolioSummaryDtoMapper.kt src/main/kotlin/ee/tenman/portfolio/service/summary/PortfolioSummarySeriesService.kt src/main/kotlin/ee/tenman/portfolio/controller/PortfolioSummaryController.kt src/test/kotlin/ee/tenman/portfolio/controller/PortfolioSummaryControllerIT.kt
git commit -m "Add portfolio summary series endpoint"
```

---

### Task 5: Warm the default series on startup

`PLATFORM_SUMMARY_CACHE` has a 1-hour TTL, so the default 6M key goes cold roughly hourly and the first user of that hour waits ~1.8 s. Startup warming at least covers the deploy case. No scheduled job — that was explicitly rejected in the spec.

**Files:**

- Modify: `src/main/kotlin/ee/tenman/portfolio/configuration/PortfolioSummaryWarmup.kt`

**Interfaces:**

- Consumes: the `/series` endpoint from Task 4.
- Produces: nothing new — two extra entries in arrays that already exist, still fired once from `ApplicationReadyEvent`.

- [ ] **Step 1: Add the default series path**

In the `companion object`, add to `BASE_PATHS` after the existing `current` entry:

```kotlin
        "/api/portfolio-summary/series?range=$DEFAULT_RANGE",
```

and add the constant alongside `HISTORICAL_PAGE_SIZE`:

```kotlin
    private const val DEFAULT_RANGE = "6M"
```

- [ ] **Step 2: Add the platform-filtered series path**

In `warmupPaths()`, extend the returned list:

```kotlin
    return BASE_PATHS +
      listOf(
        "/api/portfolio-summary/historical?page=0&size=$HISTORICAL_PAGE_SIZE&$platformQuery",
        "/api/portfolio-summary/current?$platformQuery",
        "/api/portfolio-summary/series?range=$DEFAULT_RANGE&$platformQuery",
      )
```

- [ ] **Step 3: Verify the application still starts and the paths resolve**

```bash
./gradlew compileKotlin ktlintCheck
```

Expected: PASS. The warmup itself is wrapped in `runCatching` and logs on failure, so a bad path degrades to a warning rather than a crash — check the startup log line "Warmed portfolio summary path in … ms" when the app is next run.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/configuration/PortfolioSummaryWarmup.kt
git commit -m "Warm the default chart series on startup"
```

---

### Task 6: Frontend range vocabulary and persisted selection

The eight codes are simultaneously the wire values and the chip labels, so one `as const` tuple covers both with no mapping table. The composable persists the choice and self-heals a stale stored value — `localStorage` survives app versions and is user-writable, so an unrecognised value would otherwise 400 the chart forever.

**Files:**

- Create: `ui/models/chart-range.ts`
- Create: `ui/composables/use-chart-range.ts`
- Modify: `ui/constants/storage-keys.ts`
- Test: `ui/composables/use-chart-range.test.ts`

**Interfaces:**

- Consumes: `STORAGE_KEYS` from `ui/constants` (re-exported from `ui/constants/storage-keys.ts`).
- Produces:
  - `CHART_RANGES: readonly ['1W', '1M', '6M', 'YTD', '1Y', '2Y', '3Y', 'MAX']`
  - `type ChartRange = (typeof CHART_RANGES)[number]`
  - `DEFAULT_CHART_RANGE: ChartRange` (`'6M'`)
  - `useChartRange(): { selectedRange: Ref<ChartRange>, selectRange: (range: ChartRange) => void }`
  - `STORAGE_KEYS.SUMMARY_CHART_RANGE` = `'portfolio_summary_chart_range'`

- [ ] **Step 1: Write the failing test**

Create `ui/composables/use-chart-range.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { useChartRange } from './use-chart-range'

const stored = ref<string>('6M')

vi.mock('@vueuse/core', () => ({
  useLocalStorage: () => stored,
}))

describe('useChartRange', () => {
  beforeEach(() => {
    stored.value = '6M'
  })

  it('should default to six months', () => {
    const { selectedRange } = useChartRange()

    expect(selectedRange.value).toBe('6M')
  })

  it('should persist the selected range', () => {
    const { selectedRange, selectRange } = useChartRange()

    selectRange('1Y')

    expect(selectedRange.value).toBe('1Y')
  })

  it('should fall back to the default when the stored range is unknown', () => {
    stored.value = '1D'

    const { selectedRange } = useChartRange()

    expect(selectedRange.value).toBe('6M')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd ui && npm test -- --run use-chart-range
```

Expected: FAIL — `Failed to resolve import "./use-chart-range"`.

- [ ] **Step 3: Add the storage key**

In `ui/constants/storage-keys.ts`, add to the object:

```ts
  SUMMARY_CHART_RANGE: 'portfolio_summary_chart_range',
```

- [ ] **Step 4: Write the implementation**

Create `ui/models/chart-range.ts`:

```ts
export const CHART_RANGES = ['1W', '1M', '6M', 'YTD', '1Y', '2Y', '3Y', 'MAX'] as const

export type ChartRange = (typeof CHART_RANGES)[number]

export const DEFAULT_CHART_RANGE: ChartRange = '6M'
```

Create `ui/composables/use-chart-range.ts`:

```ts
import { useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../constants'
import { CHART_RANGES, DEFAULT_CHART_RANGE, type ChartRange } from '../models/chart-range'

export function useChartRange() {
  const selectedRange = useLocalStorage<ChartRange>(
    STORAGE_KEYS.SUMMARY_CHART_RANGE,
    DEFAULT_CHART_RANGE
  )

  if (!CHART_RANGES.includes(selectedRange.value)) {
    selectedRange.value = DEFAULT_CHART_RANGE
  }

  const selectRange = (range: ChartRange) => {
    selectedRange.value = range
  }

  return { selectedRange, selectRange }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd ui && npm test -- --run use-chart-range
```

Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add ui/models/chart-range.ts ui/composables/use-chart-range.ts ui/composables/use-chart-range.test.ts ui/constants/storage-keys.ts
git commit -m "Add persisted chart range selection"
```

---

### Task 7: Chip row component

Presentational only — no fetching, no storage. Reuses the global `.platform-buttons` / `.platform-btn` / `.platform-btn.active` classes from `ui/styles/components/controls.css`, exactly as `platform-filter.vue` and `allocation-table.vue` already do, so the chips match the platform filter pixel for pixel and no new CSS is written.

**Files:**

- Create: `ui/components/portfolio/chart-range-filter.vue`
- Test: `ui/components/portfolio/chart-range-filter.test.ts`

**Interfaces:**

- Consumes: `CHART_RANGES`, `type ChartRange` from Task 6.
- Produces: a component with prop `selected: ChartRange` and emit `select: [range: ChartRange]`.

- [ ] **Step 1: Write the failing test**

Create `ui/components/portfolio/chart-range-filter.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ChartRangeFilter from './chart-range-filter.vue'

const createWrapper = (selected = '6M') => mount(ChartRangeFilter, { props: { selected } })

describe('ChartRangeFilter', () => {
  it('should render one chip per range in declaration order', () => {
    const labels = createWrapper()
      .findAll('.platform-btn')
      .map(button => button.text())

    expect(labels).toEqual(['1W', '1M', '6M', 'YTD', '1Y', '2Y', '3Y', 'MAX'])
  })

  it('should mark the selected range as active', () => {
    const active = createWrapper('1Y')
      .findAll('.platform-btn')
      .filter(button => button.classes('active'))
      .map(button => button.text())

    expect(active).toEqual(['1Y'])
  })

  it('should emit the clicked range', async () => {
    const wrapper = createWrapper()

    await wrapper.findAll('.platform-btn')[7].trigger('click')

    expect(wrapper.emitted('select')).toEqual([['MAX']])
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd ui && npm test -- --run chart-range-filter
```

Expected: FAIL — `Failed to resolve import "./chart-range-filter.vue"`.

- [ ] **Step 3: Write the implementation**

Create `ui/components/portfolio/chart-range-filter.vue`:

```vue
<template>
  <div class="platform-buttons">
    <button
      v-for="range in CHART_RANGES"
      :key="range"
      class="platform-btn"
      :class="{ active: range === selected }"
      type="button"
      @click="emit('select', range)"
    >
      {{ range }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { CHART_RANGES, type ChartRange } from '../../models/chart-range'

defineProps<{
  selected: ChartRange
}>()

const emit = defineEmits<{
  select: [range: ChartRange]
}>()
</script>
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd ui && npm test -- --run chart-range-filter
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add ui/components/portfolio/chart-range-filter.vue ui/components/portfolio/chart-range-filter.test.ts
git commit -m "Add chart range chip row component"
```

---

### Task 8: Series query in the summary composable

One extra `useQuery` keyed on `(platforms, range)`. `placeholderData: keepPreviousData` is load-bearing — without it the chart unmounts and the page jumps for the ~2.5 s of a cold range switch. The `useInfiniteQuery` that feeds the table is untouched, so a series failure cannot blank the tab.

**Files:**

- Modify: `ui/constants/api.ts`
- Modify: `ui/services/portfolio-summary-service.ts`
- Modify: `ui/composables/use-portfolio-summary-query.ts`
- Test: `ui/composables/use-portfolio-summary-query.test.ts`

**Interfaces:**

- Consumes: `type ChartRange`, `DEFAULT_CHART_RANGE` from Task 6; the `/series` endpoint from Task 4.
- Produces:
  - `API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES` = `'/portfolio-summary/series'`
  - `portfolioSummaryService.getSeries(range: ChartRange, platforms?: string[]): Promise<PortfolioSummaryDto[]>`
  - `usePortfolioSummaryQuery(selectedPlatforms?: Ref<string[]>, selectedRange?: Ref<ChartRange>)` — same return object as today plus `chartSummaries: ComputedRef<PortfolioSummaryDto[]>`. Both parameters stay optional, so existing call sites keep compiling.

- [ ] **Step 1: Write the failing test**

In `ui/composables/use-portfolio-summary-query.test.ts`, add the import:

```ts
import type { ChartRange } from '../models/chart-range'
```

extend `beforeEach` with:

```ts
vi.mocked(portfolioSummaryService.getSeries).mockResolvedValue(mockHistoricalSummaries)
```

change `setupQuery` to accept the range ref:

```ts
const setupQuery = (platforms?: Ref<string[]>, range?: Ref<ChartRange>) => {
  let queryResult: ReturnType<typeof usePortfolioSummaryQuery> | null = null

  const TestComponent = {
    setup() {
      queryResult = usePortfolioSummaryQuery(platforms, range)
      return { queryResult }
    },
    template: '<div>{{ queryResult.isLoading.value ? "Loading" : "Loaded" }}</div>',
  }

  const wrapper = renderWithProviders(TestComponent)

  return {
    queryResult: queryResult!,
    queryClient: wrapper.queryClient,
    wrapper: wrapper,
  }
}
```

and append a new describe block:

```ts
describe('chart series', () => {
  it('should request the default range when none is provided', async () => {
    const { queryResult } = setupQuery()

    await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })
    await flushPromises()

    expect(portfolioSummaryService.getSeries).toHaveBeenCalledWith('6M', undefined)
  })

  it('should request the selected range with the selected platforms', async () => {
    const platforms = ref(['LIGHTYEAR'])
    const range = ref<ChartRange>('1Y')
    const { queryResult } = setupQuery(platforms, range)

    await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })
    await flushPromises()

    expect(portfolioSummaryService.getSeries).toHaveBeenCalledWith('1Y', ['LIGHTYEAR'])
  })

  it('should refetch when the range changes', async () => {
    const range = ref<ChartRange>('6M')
    const { queryResult } = setupQuery(undefined, range)

    await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })
    await flushPromises()

    range.value = 'MAX'
    await flushPromises()

    await vi.waitFor(
      () => expect(portfolioSummaryService.getSeries).toHaveBeenCalledWith('MAX', undefined),
      { timeout: 5000 }
    )
  })

  it('should merge the current summary into the chart series', async () => {
    const { queryResult } = setupQuery()

    await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })
    await flushPromises()

    await vi.waitFor(() => queryResult.chartSummaries.value.length === 3, { timeout: 5000 })

    expect(queryResult.chartSummaries.value.map(s => s.date)).toContain('2023-12-31')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd ui && npm test -- --run use-portfolio-summary-query
```

Expected: FAIL — `portfolioSummaryService.getSeries is not a function` / `chartSummaries` undefined.

- [ ] **Step 3: Add the endpoint constant**

In `ui/constants/api.ts`, add after `PORTFOLIO_SUMMARY_CURRENT`:

```ts
  PORTFOLIO_SUMMARY_SERIES: '/portfolio-summary/series',
```

- [ ] **Step 4: Add the service method**

In `ui/services/portfolio-summary-service.ts`, add the import:

```ts
import type { ChartRange } from '../models/chart-range'
```

and add the method after `getCurrent`:

```ts
  getSeries: (range: ChartRange, platforms?: string[]) =>
    httpClient.get<PortfolioSummaryDto[]>(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES, {
      params: { range, ...(platforms?.length ? { platforms } : {}) },
    }),
```

- [ ] **Step 5: Add the series query**

In `ui/composables/use-portfolio-summary-query.ts`, change the imports:

```ts
import {
  useQuery,
  useMutation,
  useQueryClient,
  useInfiniteQuery,
  keepPreviousData,
} from '@tanstack/vue-query'
import { DEFAULT_CHART_RANGE, type ChartRange } from '../models/chart-range'
```

change the signature:

```ts
export function usePortfolioSummaryQuery(
  selectedPlatforms?: Ref<string[]>,
  selectedRange?: Ref<ChartRange>
) {
```

add after the `activePlatforms` computed:

```ts
const rangeKey = computed(() => selectedRange?.value ?? DEFAULT_CHART_RANGE)
```

add after the `currentSummary` query:

```ts
const { data: seriesData } = useQuery({
  queryKey: ['portfolio-summary', 'series', platformsKey, rangeKey],
  queryFn: () => portfolioSummaryService.getSeries(rangeKey.value, activePlatforms.value),
  placeholderData: keepPreviousData,
  enabled: isAuthenticated,
})
```

add after the `summaries` computed:

```ts
const chartSummaries = computed(() =>
  mergeHistoricalWithCurrent(seriesData.value ?? [], currentSummary.value)
)
```

and add `chartSummaries,` to the returned object, immediately after `summaries,`.

- [ ] **Step 6: Run the test to verify it passes**

```bash
cd ui && npm test -- --run use-portfolio-summary-query
```

Expected: PASS, including the four new tests and all pre-existing ones.

- [ ] **Step 7: Commit**

```bash
git add ui/constants/api.ts ui/services/portfolio-summary-service.ts ui/composables/use-portfolio-summary-query.ts ui/composables/use-portfolio-summary-query.test.ts
git commit -m "Fetch the chart series per selected range"
```

---

### Task 9: Wire the chips into the summary tab

The chart reads `chartSummaries` (range-scoped) while the table keeps reading `reversedSummaries` (paginated), so the two diverge deliberately and infinite scroll is untouched.

**Files:**

- Modify: `ui/components/portfolio-summary.vue`

**Interfaces:**

- Consumes: `useChartRange` (Task 6), `chart-range-filter.vue` (Task 7), `chartSummaries` from `usePortfolioSummaryQuery` (Task 8).
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Add the imports**

In the `<script setup>` block of `ui/components/portfolio-summary.vue`, add alongside the other composable imports:

```ts
import { useChartRange } from '../composables/use-chart-range'
import ChartRangeFilter from './portfolio/chart-range-filter.vue'
```

- [ ] **Step 2: Wire the composables**

Add immediately above the `usePortfolioSummaryQuery` call:

```ts
const { selectedRange, selectRange } = useChartRange()
```

change the destructure to pull in `chartSummaries` and pass the range:

```ts
const {
  summaries,
  chartSummaries,
  reversedSummaries,
  isLoading,
  isRecalculating,
  isFetching,
  error,
  recalculationMessage,
  recalculate,
  fetchSummaries,
  hasMoreData,
} = usePortfolioSummaryQuery(selectedPlatforms, selectedRange)
```

and change the chart source:

```ts
const { processedChartData } = usePortfolioChart(chartSummaries)
```

`summaries` stays in the destructure — `viewState` still reads it, so the tab's loading/empty/error behaviour remains driven by the table query alone.

- [ ] **Step 3: Render the chip row**

In the template, replace the single chart line:

```html
<portfolio-chart :key="chartKey" :data="processedChartData" />
```

with:

```html
<portfolio-chart :key="chartKey" :data="processedChartData" />

<chart-range-filter class="mt-2" :selected="selectedRange" @select="selectRange" />
```

- [ ] **Step 4: Verify types, lint and the full unit suite**

```bash
cd ui && npm run lint-format && npm test -- --run
```

Expected: PASS. Then discard the generated-file churn:

```bash
git checkout ui/models/generated/domain-models.ts
```

- [ ] **Step 5: Check for unused exports**

```bash
cd ui && npm run check-unused
```

Expected: no new findings under `ui/models/chart-range.ts`, `ui/composables/use-chart-range.ts` or `ui/components/portfolio/chart-range-filter.vue`. Ignore any pre-existing findings and anything under `.claude/worktrees/`.

- [ ] **Step 6: Commit**

```bash
git add ui/components/portfolio-summary.vue
git commit -m "Show the range chips under the summary chart"
```

---

### Task 10: Visual regression stub and baselines

`stubPortfolioSummary` is used by every `/`-rendering visual test. Without a `/series` stub the request escapes to the real backend and the chart renders from nothing, so this must land before any baseline is re-recorded.

**Files:**

- Modify: `ui/tests/visual/summary-fixture.ts`
- Modify (binary): `ui/tests/visual/*-snapshots/` baselines that actually change

**Interfaces:**

- Consumes: `API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES` (Task 8), `apiRoute` from `ui/tests/visual/stub.ts`.
- Produces: the `/series` route stub, returning the same 186 rows the historical stub returns, as a bare array rather than a `Page`.

- [ ] **Step 1: Add the stub**

In `ui/tests/visual/summary-fixture.ts`, extract the mapped rows to a const above `HISTORICAL_RESPONSE`:

```ts
const SERIES_RESPONSE = HISTORICAL_ROWS.map(toSummary)
```

reuse it in the existing page response:

```ts
const HISTORICAL_RESPONSE = {
  content: SERIES_RESPONSE,
  totalPages: 1,
  totalElements: HISTORICAL_ROWS.length,
  size: HISTORICAL_ROWS.length,
  number: 0,
} satisfies Page<PortfolioSummaryDto>
```

and add the route inside `stubPortfolioSummary`, before the platforms route:

```ts
await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES), route =>
  route.fulfill({ json: SERIES_RESPONSE })
)
```

- [ ] **Step 2: Run the visual suite to see which baselines actually moved**

```bash
cd ui && npm run visual
```

Expected: reds on the baselines whose rendered page now contains a chip row — at minimum `route-summary.png` across every project, plus the desktop `modal-confirm.png` and `toast-{success,error,info,warning}.png`, which all render `/`. `state-loading.png` renders the skeleton branch, where the chips do not exist, so it should stay green; if it goes red, investigate rather than re-record.

- [ ] **Step 3: Re-record only the baselines that legitimately changed**

```bash
cd ui && npm run visual:update
```

- [ ] **Step 4: Confirm the suite is green and the diff is only the expected files**

```bash
cd ui && npm run visual
git status --short ui/tests/visual
```

Expected: PASS, and the changed `.png` set matches the reds observed in Step 2. Baselines recorded against the live dev DB decay within hours — if unrelated baselines are red, the cause is unstubbed live data, not this change.

- [ ] **Step 5: Commit**

```bash
git add ui/tests/visual
git commit -m "Stub the series route in visual tests"
```

---

### Task 11: Full-suite verification

**Files:** none.

**Interfaces:** none.

- [ ] **Step 1: Run the backend suite**

```bash
./gradlew ktlintCheck detekt test
```

Expected: PASS. `detektMain`/`detektTest` are not the CI gate and report ~104 pre-existing type-resolution issues — do not run them and do not fix them here.

- [ ] **Step 2: Run the frontend suite**

```bash
cd ui && npm run lint-format && npm test -- --run && npm run build
```

Expected: PASS. Discard the generated-file churn afterwards:

```bash
git checkout ui/models/generated/domain-models.ts
```

- [ ] **Step 3: Manually verify the success criteria**

Start the stack with `npm run dev`, open the Portfolio tab in a fresh browser profile, and confirm:

- the chip row renders directly under the chart with `6M` active, and the chart covers 181 days plus today's live point
- clicking `1Y` issues exactly one `GET /api/portfolio-summary/series?range=1Y&platforms=…` (check the Network tab) and redraws the chart
- clicking `MAX` returns well inside the 10 s axios timeout on a cold cache
- reloading the page keeps the last selected chip
- the table's rows, sort order and infinite scroll are identical regardless of the selected chip
- switching chips does not blank the chart — the previous line stays on screen while the new one loads

- [ ] **Step 4: Open the pull request**

```bash
git push -u origin feature/portfolio-chart-time-range
gh pr create --title "Add a time-range selector to the portfolio chart" --body "$(cat <<'EOF'
## Summary

- Adds a `1W · 1M · 6M · YTD · 1Y · 2Y · 3Y · MAX` chip row under the Portfolio Summary chart
- Adds `GET /api/portfolio-summary/series`, backed by a new `SummaryRange` enum that caps every window at 366 computed points so MAX costs no more than 1Y
- Leaves the table's paginated infinite scroll untouched

## Test plan

- [ ] `./gradlew ktlintCheck detekt test`
- [ ] `cd ui && npm run lint-format && npm test -- --run`
- [ ] `cd ui && npm run visual`
- [ ] Manual: chips render, each issues one request, selection survives reload, MAX returns inside the timeout
EOF
)"
```

`git push` and `gh pr create` fail under the Bash sandbox — run them with the sandbox disabled.

---

## Self-Review

**1. Spec coverage**

| Spec section                                            | Task                                                                                                                  |
| ------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `domain/SummaryRange.kt`                                | 1                                                                                                                     |
| `configuration/SummaryRangeConverter.kt`                | 2                                                                                                                     |
| `SummaryService.getSeriesForPlatforms` / `getSeries`    | 3                                                                                                                     |
| `PlatformSummaryCacheService` `@Cacheable`              | 4                                                                                                                     |
| `PortfolioSummarySeriesService`                         | 4                                                                                                                     |
| Shared DTO mapper (pre-flight decision, see below)      | 4                                                                                                                     |
| `controller` `/series`                                  | 4                                                                                                                     |
| `PortfolioSummaryWarmup`                                | 5                                                                                                                     |
| `build.gradle.kts` typescript-generator                 | **Dropped** — documented under "Deviation from the spec", replaced by `ui/models/chart-range.ts` in Task 6            |
| `ui/constants/api.ts`                                   | 8                                                                                                                     |
| `ui/constants/storage-keys.ts`                          | 6                                                                                                                     |
| `ui/services/portfolio-summary-service.ts`              | 8                                                                                                                     |
| `ui/composables/use-chart-range.ts`                     | 6                                                                                                                     |
| `ui/components/portfolio/chart-range-filter.vue`        | 7                                                                                                                     |
| `ui/composables/use-portfolio-summary-query.ts`         | 8                                                                                                                     |
| `ui/components/portfolio-summary.vue`                   | 9                                                                                                                     |
| Error handling (400, viewState isolation, empty series) | 4 (400 test), 9 (`summaries` still drives `viewState`), inherent (`usePortfolioChart` returns null for an empty list) |
| Kotlin tests                                            | 1, 2, 3, 4                                                                                                            |
| Vitest tests                                            | 6, 7, 8                                                                                                               |
| Visual regression                                       | 10                                                                                                                    |
| Success criteria                                        | 11 Step 3                                                                                                             |

**2. Placeholder scan** — every code step carries real code. The only conditional instructions are two explicit fallbacks with stated triggers and verification commands (the `SummaryService` constructor argument style in Task 3 Step 1, and the 400-vs-500 expectation in Task 4 Step 1).

**3. Type consistency** — `SummaryRange.dates`/`startDate`/`sample`/`from`/`MAX_POINTS` are named identically in Tasks 1–4. `getSeriesForPlatforms(platforms, range)` keeps the same parameter order in `SummaryService`, `PlatformSummaryCacheService` and the cache key. `ChartRange`/`CHART_RANGES`/`DEFAULT_CHART_RANGE` are named identically in Tasks 6–9. `chartSummaries` is produced in Task 8 and consumed in Task 9. `PORTFOLIO_SUMMARY_SERIES` is defined in Task 8 and consumed in Task 10.

**Decided (pre-flight):** the entity → DTO mapping is extracted to a shared `PortfolioSummaryDtoMapper.kt` rather than duplicated into the series service. This was raised with the user before execution and confirmed. It means Task 4 edits the controller's existing `/current` and `/historical` mapping paths; the pre-existing `PortfolioSummaryControllerIT` tests are the regression gate.

**Decided:** 6M is `minusMonths(6)` = 181 days, five days shorter than today's fixed 186-row page. This was raised with the user and confirmed — the calendar-correct definition wins over preserving the exact current window. Do not change it to `minusDays(186)`.
