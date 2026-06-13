# Aviva Pension: GBP Conversion Hardening + CSUS Look-Through Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pin the FT-quoted-in-pounds assumption with a real conversion test, make the ECB CSV parser fail loud on format breaks, and add a daily CSUS holdings feed that attaches BlackRock MSCI-USA holdings to the existing Aviva pension instrument for sector/holding diversification look-through.

**Architecture:** Three independent units on the existing `feature/aviva-pension-gbp-eur` branch. Unit 1 adds tests only. Unit 2 is a one-method change + test. Unit 3 mirrors the existing ECB-Feign-client and Trading212-holdings-job patterns to fetch BlackRock's daily `.ajax` CSV, parse it (quote-aware), map to the canonical `HoldingData` DTO, and persist via the existing `EtfHoldingService.saveHoldings(...)` against symbol `GB00B0ZDNB53:GBP`. The diversification breakdown picks up the Aviva instrument automatically because it is an `FT`-provider instrument with positions and net-positive units — no allowlist or config entry.

**Tech Stack:** Kotlin 2.3, Spring Boot 4.0, Spring Cloud OpenFeign, Atrium 1.3 assertions, MockK, JUnit 5, Testcontainers (`@IntegrationTest`), Gradle.

**Spec:** `docs/superpowers/specs/2026-06-13-aviva-pension-csus-design.md`

**Conventions (repo overrides):** Commit subjects use an uppercase imperative verb, **no** `feat:`/`fix:` prefix, max 50 chars. No code comments. No AI attribution in commits. Method bodies have no blank lines. Atrium `toEqualNumerically` for BigDecimal. CI gate reproduced locally with `./gradlew ktlintCheck detekt`.

---

## File Structure

**Unit 1 — Pin GBP-unit assumption (tests only)**
- Modify: `src/test/kotlin/ee/tenman/portfolio/ft/HistoricalPricesServiceTest.kt` — add a test that wires a *real* `CurrencyConversionService` (mocked repo) so FT pounds → EUR is exercised end-to-end.
- Modify: `src/test/kotlin/ee/tenman/portfolio/repository/AvivaPensionMigrationIT.kt` — add a structure guard over the applied migration.

**Unit 2 — ECB parser fail-loud**
- Modify: `src/main/kotlin/ee/tenman/portfolio/ecb/EcbCsvParser.kt`
- Modify: `src/test/kotlin/ee/tenman/portfolio/ecb/EcbCsvParserTest.kt`

**Unit 3 — CSUS holdings (new `blackrock` package)**
- Create: `src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockHolding.kt`
- Create: `src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockCsvParser.kt`
- Create: `src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockHoldingsClient.kt`
- Create: `src/main/kotlin/ee/tenman/portfolio/blackrock/CsusHoldingsService.kt`
- Create: `src/main/kotlin/ee/tenman/portfolio/job/CsusHoldingsRetrievalJob.kt`
- Modify: `src/main/resources/application.yml` — add `blackrock.url`
- Create: `src/test/kotlin/ee/tenman/portfolio/blackrock/BlackRockCsvParserTest.kt`
- Create: `src/test/kotlin/ee/tenman/portfolio/blackrock/CsusHoldingsServiceTest.kt`
- Create: `src/test/kotlin/ee/tenman/portfolio/job/CsusHoldingsRetrievalJobTest.kt`

---

## Task 1: Exercise the real GBP→EUR conversion through HistoricalPricesService

The existing `HistoricalPricesServiceTest` globally stubs `convertDailyPricesToEur` to a passthrough, so the divide-by-rate path and the pounds-not-pence magnitude are never exercised through the FT service. This test wires a real `CurrencyConversionService` (with a mocked repository) into a fresh service instance and proves FT close `11.74` GBP at rate `0.85` becomes `~13.81` EUR — failing loudly if FT ever switches to pence.

**Files:**
- Modify: `src/test/kotlin/ee/tenman/portfolio/ft/HistoricalPricesServiceTest.kt`

- [ ] **Step 1: Write the failing test**

Add these imports if not already present (the file already imports `Currency`, `DailyPriceData`, `BigDecimal`, `Clock`, `LocalDate`, `ZoneId`, `mockk`, `every`):

```kotlin
import ee.tenman.portfolio.domain.ExchangeRate
import ee.tenman.portfolio.repository.ExchangeRateRepository
```

Add this test method to the `HistoricalPricesServiceTest` class:

```kotlin
  @Test
  fun `should convert ft pounds close to eur using real conversion service`() {
    val exchangeRateRepository = mockk<ExchangeRateRepository>()
    every {
      exchangeRateRepository.findAllByBaseCurrencyAndQuoteCurrencyAndEntryDateBetween(
        Currency.EUR,
        Currency.GBP,
        any(),
        any(),
      )
    } returns listOf(ExchangeRate(LocalDate.of(2025, 1, 17), Currency.EUR, Currency.GBP, BigDecimal("0.85")))
    val realConversionService = CurrencyConversionService(exchangeRateRepository)
    val clock = Clock.fixed(LocalDate.of(2025, 1, 20).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))
    val serviceWithRealConversion = HistoricalPricesService(historicalPricesClient, realConversionService, clock)
    every {
      historicalPricesClient.getHistoricalPrices(any(), any(), "543017012")
    } returns
      HistoricalPricesResponse(
        html =
          """<tr>
          <td class="mod-ui-table__cell--text">
            <span class="mod-ui-hide-small-below">Friday, January 17, 2025</span>
          </td>
          <td>11.70</td>
          <td>11.80</td>
          <td>11.65</td>
          <td>11.74</td>
          <td>1000</td>
        </tr>""",
      )

    val result = serviceWithRealConversion.fetchPrices("GB00B0ZDNB53:GBP")

    expect(result[LocalDate.of(2025, 1, 17)]?.close).notToEqualNull().toEqualNumerically(BigDecimal("13.8117647059"))
  }
```

- [ ] **Step 2: Run the test to verify it passes (it documents existing correct behavior)**

Run: `./gradlew test --tests "ee.tenman.portfolio.ft.HistoricalPricesServiceTest"`
Expected: PASS — `11.74 / 0.85 = 13.8117647059` at scale 10, HALF_UP. If it FAILS with a value near `1381`, FT changed to pence and the conversion needs a `/100`. This is the regression guard.

(Note: this is a characterization test — the production code is already correct, verified live. The test exists to *lock* that correctness, replacing the stub's false confidence.)

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/ee/tenman/portfolio/ft/HistoricalPricesServiceTest.kt
git commit -m "Pin FT pounds to EUR conversion with real service"
```

---

## Task 2: Guard the Aviva migration structure

Freeze the reconciled migration so an accidental edit cannot silently drift the modeled units. Asserts net units, the six fee SELLs at near-zero price, and the BUY count, against the migration applied in Testcontainers.

**Files:**
- Modify: `src/test/kotlin/ee/tenman/portfolio/repository/AvivaPensionMigrationIT.kt`

- [ ] **Step 1: Write the failing test**

Add these test methods to the `AvivaPensionMigrationIT` class (the class already has `jdbcTemplate` and imports `BigDecimal`, `toEqual`, `expect`):

```kotlin
  @Test
  fun `should have twenty four buy transactions for aviva`() {
    val buys =
      jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM portfolio_transaction WHERE platform = 'AVIVA' AND transaction_type = 'BUY'",
        Int::class.java,
      )
    expect(buys).toEqual(24)
  }

  @Test
  fun `should model six annual fee sells at near zero price for aviva`() {
    val fees =
      jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM portfolio_transaction WHERE platform = 'AVIVA' AND transaction_type = 'SELL' AND price = 0.00000001",
        Int::class.java,
      )
    expect(fees).toEqual(6)
  }

  @Test
  fun `should hold net positive units after buys minus fee sells for aviva`() {
    val netUnits =
      jdbcTemplate.queryForObject(
        """
        SELECT SUM(CASE WHEN transaction_type = 'BUY' THEN quantity ELSE -quantity END)
        FROM portfolio_transaction WHERE platform = 'AVIVA'
        """.trimIndent(),
        BigDecimal::class.java,
      )
    expect(netUnits.compareTo(BigDecimal.ZERO)).toEqual(1)
  }
```

- [ ] **Step 2: Run the tests to verify they pass**

Run: `./gradlew test --tests "ee.tenman.portfolio.repository.AvivaPensionMigrationIT"`
Expected: PASS. The migration has 24 BUY rows, 6 SELL fee rows at `0.00000001`, and net units > 0 (`compareTo(ZERO) == 1`).

If `should have twenty four buy transactions` fails, re-count the BUY rows in `V202606111010__aviva_pension_transactions.sql` and set the expected number to the actual count — the goal is to freeze whatever the reconciled migration contains, not to assert an arbitrary number.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/ee/tenman/portfolio/repository/AvivaPensionMigrationIT.kt
git commit -m "Guard Aviva migration transaction structure"
```

---

## Task 3: Make EcbCsvParser fail loud on header format breaks

`parse()` currently returns `emptyList()` when the `TIME_PERIOD`/`OBS_VALUE` header is missing — a silent failure that would quietly stop the ECB rate backfill. Make it throw instead, while keeping genuinely-empty input (`< 2` lines) returning `emptyList()`.

**Files:**
- Modify: `src/main/kotlin/ee/tenman/portfolio/ecb/EcbCsvParser.kt`
- Modify: `src/test/kotlin/ee/tenman/portfolio/ecb/EcbCsvParserTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `EcbCsvParserTest` (it already imports `toBeEmpty`, `toContainExactly`, `expect`; add `toThrow` and `messageToContain`):

```kotlin
import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
```

```kotlin
  @Test
  fun `should throw when header lacks required columns`() {
    val csv = listOf("KEY,FREQ,CURRENCY,WRONG_DATE,WRONG_VALUE", "EXR.D.GBP,D,GBP,2026-06-10,0.86").joinToString("\n")

    expect {
      EcbCsvParser.parse(csv)
    }.toThrow<IllegalStateException>().messageToContain("OBS_VALUE")
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "ee.tenman.portfolio.ecb.EcbCsvParserTest"`
Expected: FAIL — `should throw when header lacks required columns` fails because `parse` currently returns `emptyList()` instead of throwing.

- [ ] **Step 3: Make it fail loud**

In `src/main/kotlin/ee/tenman/portfolio/ecb/EcbCsvParser.kt`, replace the early-return guard with a check. Change:

```kotlin
    if (dateIndex < 0 || rateIndex < 0) return emptyList()
```

to:

```kotlin
    check(dateIndex >= 0 && rateIndex >= 0) { "ECB CSV header missing TIME_PERIOD or OBS_VALUE columns, found: ${lines.first()}" }
```

- [ ] **Step 4: Run the whole parser test class to verify all pass**

Run: `./gradlew test --tests "ee.tenman.portfolio.ecb.EcbCsvParserTest"`
Expected: PASS — the new throw test passes; the existing blank/blank-value/malformed-date/happy-path tests stay green (blank input still hits the `lines.size < 2` early return).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/ecb/EcbCsvParser.kt src/test/kotlin/ee/tenman/portfolio/ecb/EcbCsvParserTest.kt
git commit -m "Fail loud when ECB CSV header columns missing"
```

---

## Task 4: BlackRockHolding model + quote-aware CSV parser

BlackRock's `.ajax` CSV has a 2-line preamble, a header row starting with `Ticker,`, then quote-wrapped rows whose numbers contain commas (e.g. `"351,722,454.96"`). The parser must split quote-aware, skip the preamble, keep `Asset Class == Equity` rows, and fail loud if the header is absent.

**Files:**
- Create: `src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockHolding.kt`
- Create: `src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockCsvParser.kt`
- Create: `src/test/kotlin/ee/tenman/portfolio/blackrock/BlackRockCsvParserTest.kt`

- [ ] **Step 1: Create the model**

`src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockHolding.kt`:

```kotlin
package ee.tenman.portfolio.blackrock

import java.math.BigDecimal

data class BlackRockHolding(
  val ticker: String?,
  val name: String,
  val sector: String?,
  val weight: BigDecimal,
)
```

- [ ] **Step 2: Write the failing parser test**

`src/test/kotlin/ee/tenman/portfolio/blackrock/BlackRockCsvParserTest.kt`:

```kotlin
package ee.tenman.portfolio.blackrock

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BlackRockCsvParserTest {
  private val header = "Ticker,Name,Sector,Asset Class,Market Value,Weight (%),Notional Value,Shares,Price,Location,Exchange,Market Currency"

  private fun csv(vararg rows: String): String = listOf("Fund Holdings as of,\"11/Jun/2026\"", " ", header, *rows).joinToString("\n")

  @Test
  fun `should parse equity holdings skipping preamble`() {
    val csv =
      csv(
        "\"NVDA\",\"NVIDIA CORP\",\"Information Technology\",\"Equity\",\"351,722,454.96\",\"7.42\",\"351,722,454.96\",\"1,716,808.00\",\"204.87\",\"United States\",\"NASDAQ\",\"USD\"",
        "\"AAPL\",\"APPLE INC\",\"Information Technology\",\"Equity\",\"322,775,337.86\",\"6.81\",\"322,775,337.86\",\"1,091,822.00\",\"295.63\",\"United States\",\"NASDAQ\",\"USD\"",
      )

    val holdings = BlackRockCsvParser.parse(csv)

    expect(holdings).toHaveSize(2)
    expect(holdings.first().ticker).toEqual("NVDA")
    expect(holdings.first().name).toEqual("NVIDIA CORP")
    expect(holdings.first().sector).toEqual("Information Technology")
    expect(holdings.first().weight).toEqualNumerically(BigDecimal("7.42"))
  }

  @Test
  fun `should skip non equity rows`() {
    val csv =
      csv(
        "\"NVDA\",\"NVIDIA CORP\",\"Information Technology\",\"Equity\",\"1\",\"7.42\",\"1\",\"1\",\"204.87\",\"United States\",\"NASDAQ\",\"USD\"",
        "\"USD\",\"US DOLLAR\",\"Cash and/or Derivatives\",\"Cash\",\"1\",\"0.20\",\"1\",\"1\",\"1.00\",\"United States\",\"-\",\"USD\"",
      )

    val holdings = BlackRockCsvParser.parse(csv)

    expect(holdings).toHaveSize(1)
    expect(holdings.first().ticker).toEqual("NVDA")
  }

  @Test
  fun `should skip malformed rows without losing the feed`() {
    val csv =
      csv(
        "\"NVDA\",\"NVIDIA CORP\",\"Information Technology\",\"Equity\",\"1\",\"7.42\",\"1\",\"1\",\"204.87\",\"United States\",\"NASDAQ\",\"USD\"",
        "\"BAD\",\"BAD ROW\",\"Information Technology\",\"Equity\",\"1\",\"not-a-number\",\"1\",\"1\",\"1.00\",\"United States\",\"NASDAQ\",\"USD\"",
      )

    val holdings = BlackRockCsvParser.parse(csv)

    expect(holdings).toHaveSize(1)
    expect(holdings.first().ticker).toEqual("NVDA")
  }

  @Test
  fun `should throw when ticker header row is absent`() {
    val csv = "Fund Holdings as of,\"11/Jun/2026\"\nsome,unexpected,content"

    expect {
      BlackRockCsvParser.parse(csv)
    }.toThrow<IllegalStateException>().messageToContain("Ticker")
  }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew test --tests "ee.tenman.portfolio.blackrock.BlackRockCsvParserTest"`
Expected: FAIL — `BlackRockCsvParser` does not exist yet (compilation error).

- [ ] **Step 4: Implement the parser**

`src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockCsvParser.kt`:

```kotlin
package ee.tenman.portfolio.blackrock

import org.slf4j.LoggerFactory
import java.math.BigDecimal

object BlackRockCsvParser {
  private val log = LoggerFactory.getLogger(javaClass)
  private const val EQUITY = "Equity"
  private val REQUIRED = listOf("Ticker", "Name", "Sector", "Asset Class", "Weight (%)")

  fun parse(csv: String): List<BlackRockHolding> {
    val lines = csv.lines().filter { it.isNotBlank() }
    val headerIndex = lines.indexOfFirst { it.startsWith("Ticker,") }
    check(headerIndex >= 0) { "BlackRock holdings CSV missing 'Ticker,' header row" }
    val columns = splitCsvLine(lines[headerIndex])
    val indices = REQUIRED.associateWith { columns.indexOf(it) }
    check(indices.values.all { it >= 0 }) { "BlackRock holdings CSV missing required columns, found: $columns" }
    return lines.drop(headerIndex + 1).mapNotNull { parseRow(it, indices) }
  }

  private fun parseRow(
    line: String,
    indices: Map<String, Int>,
  ): BlackRockHolding? {
    val cells = splitCsvLine(line)
    if (cells.size <= indices.values.max()) return null
    if (cells[indices.getValue("Asset Class")].trim() != EQUITY) return null
    return runCatching {
      BlackRockHolding(
        ticker = cells[indices.getValue("Ticker")].trim().ifBlank { null },
        name = cells[indices.getValue("Name")].trim(),
        sector = cells[indices.getValue("Sector")].trim().ifBlank { null },
        weight = BigDecimal(cells[indices.getValue("Weight (%)")].trim()),
      )
    }.onFailure { log.warn("Skipping malformed BlackRock holding row '$line'") }.getOrNull()
  }

  private fun splitCsvLine(line: String): List<String> {
    val state =
      line.fold(CsvSplitState()) { acc, char ->
        when {
          char == '"' -> acc.copy(inQuotes = !acc.inQuotes)
          char == ',' && !acc.inQuotes -> acc.copy(fields = acc.fields + acc.current, current = "")
          else -> acc.copy(current = acc.current + char)
        }
      }
    return state.fields + state.current
  }
}

private data class CsvSplitState(
  val fields: List<String> = emptyList(),
  val current: String = "",
  val inQuotes: Boolean = false,
)
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "ee.tenman.portfolio.blackrock.BlackRockCsvParserTest"`
Expected: PASS — all four tests green.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockHolding.kt src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockCsvParser.kt src/test/kotlin/ee/tenman/portfolio/blackrock/BlackRockCsvParserTest.kt
git commit -m "Add BlackRock holdings CSV parser"
```

---

## Task 5: BlackRockHoldingsClient (Feign)

A Feign client mirroring `EcbClient`, hitting BlackRock's `.ajax` endpoint with a browser `User-Agent` (required, or BlackRock returns a block page) and returning the raw CSV string (string decoding is already configured on this branch).

**Files:**
- Create: `src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockHoldingsClient.kt`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Create the client**

`src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockHoldingsClient.kt`:

```kotlin
package ee.tenman.portfolio.blackrock

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
  name = "blackRockHoldingsClient",
  url = "\${blackrock.url:https://www.blackrock.com}",
)
interface BlackRockHoldingsClient {
  @GetMapping(
    value = ["/uk/individual/products/{productId}/x/1472631233320.ajax"],
    headers = ["User-Agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"],
  )
  fun getHoldingsCsv(
    @PathVariable productId: String,
    @RequestParam fileName: String,
    @RequestParam fileType: String,
    @RequestParam dataType: String,
  ): String
}
```

- [ ] **Step 2: Add the base URL to application.yml**

In `src/main/resources/application.yml`, next to the existing `ecb:` block (around line 263), add:

```yaml
blackrock:
  url: https://www.blackrock.com
```

- [ ] **Step 3: Compile to verify the client and config are valid**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (no test — the client is exercised via the service test in Task 6 with a mock, mirroring how `EcbClient` has no standalone unit test).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/blackrock/BlackRockHoldingsClient.kt src/main/resources/application.yml
git commit -m "Add BlackRock holdings Feign client"
```

---

## Task 6: CsusHoldingsService

Encapsulates the CSUS-specific fetch: calls the client with the CSUS product id, parses, sorts by weight descending, and maps to ranked `HoldingData`. `@Retryable` like `Trading212HoldingsService`.

**Files:**
- Create: `src/main/kotlin/ee/tenman/portfolio/blackrock/CsusHoldingsService.kt`
- Create: `src/test/kotlin/ee/tenman/portfolio/blackrock/CsusHoldingsServiceTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ee/tenman/portfolio/blackrock/CsusHoldingsServiceTest.kt`:

```kotlin
package ee.tenman.portfolio.blackrock

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CsusHoldingsServiceTest {
  private val client = mockk<BlackRockHoldingsClient>()
  private val service = CsusHoldingsService(client)

  private val header = "Ticker,Name,Sector,Asset Class,Market Value,Weight (%),Notional Value,Shares,Price,Location,Exchange,Market Currency"

  private fun row(
    ticker: String,
    weight: String,
  ): String = "\"$ticker\",\"$ticker CORP\",\"Information Technology\",\"Equity\",\"1\",\"$weight\",\"1\",\"1\",\"1.00\",\"United States\",\"NASDAQ\",\"USD\""

  @Test
  fun `should return holdings ranked by descending weight`() {
    every { client.getHoldingsCsv(any(), any(), any(), any()) } returns
      listOf("Fund Holdings as of,\"11/Jun/2026\"", " ", header, row("AAPL", "6.81"), row("NVDA", "7.42")).joinToString("\n")

    val holdings = service.fetchHoldings()

    expect(holdings).toHaveSize(2)
    expect(holdings.first().ticker).toEqual("NVDA")
    expect(holdings.first().rank).toEqual(1)
    expect(holdings.first().weight).toEqualNumerically(BigDecimal("7.42"))
    expect(holdings[1].ticker).toEqual("AAPL")
    expect(holdings[1].rank).toEqual(2)
  }

  @Test
  fun `should return empty list when csv has no equity rows`() {
    every { client.getHoldingsCsv(any(), any(), any(), any()) } returns
      listOf("Fund Holdings as of,\"11/Jun/2026\"", " ", header).joinToString("\n")

    val holdings = service.fetchHoldings()

    expect(holdings).toHaveSize(0)
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "ee.tenman.portfolio.blackrock.CsusHoldingsServiceTest"`
Expected: FAIL — `CsusHoldingsService` does not exist yet (compilation error).

- [ ] **Step 3: Implement the service**

`src/main/kotlin/ee/tenman/portfolio/blackrock/CsusHoldingsService.kt`:

```kotlin
package ee.tenman.portfolio.blackrock

import ee.tenman.portfolio.dto.HoldingData
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class CsusHoldingsService(
  private val blackRockHoldingsClient: BlackRockHoldingsClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchHoldings(): List<HoldingData> {
    val csv = blackRockHoldingsClient.getHoldingsCsv(PRODUCT_ID, "${FUND}_holdings", "csv", "fund")
    val holdings = BlackRockCsvParser.parse(csv)
    if (holdings.isEmpty()) {
      log.warn("BlackRock returned 0 equity holdings for $FUND")
      return emptyList()
    }
    return holdings
      .sortedByDescending { it.weight }
      .mapIndexed { index, holding ->
        HoldingData(
          name = holding.name,
          ticker = holding.ticker,
          sector = holding.sector,
          weight = holding.weight,
          rank = index + 1,
        )
      }
  }

  companion object {
    private const val PRODUCT_ID = "253740"
    private const val FUND = "CSUS"
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "ee.tenman.portfolio.blackrock.CsusHoldingsServiceTest"`
Expected: PASS — both tests green (NVDA ranks first by weight, empty CSV yields empty list).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/blackrock/CsusHoldingsService.kt src/test/kotlin/ee/tenman/portfolio/blackrock/CsusHoldingsServiceTest.kt
git commit -m "Add CSUS holdings fetch service"
```

---

## Task 7: CsusHoldingsRetrievalJob

A scheduled job mirroring `Trading212HoldingsRetrievalJob`: idempotent (skips if holdings already exist for today), persists via `EtfHoldingService.saveHoldings(...)` against the Aviva symbol, evicts the breakdown cache, and records a `JobExecution`.

**Files:**
- Create: `src/main/kotlin/ee/tenman/portfolio/job/CsusHoldingsRetrievalJob.kt`
- Create: `src/test/kotlin/ee/tenman/portfolio/job/CsusHoldingsRetrievalJobTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ee/tenman/portfolio/job/CsusHoldingsRetrievalJobTest.kt`:

```kotlin
package ee.tenman.portfolio.job

import ee.tenman.portfolio.blackrock.CsusHoldingsService
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class CsusHoldingsRetrievalJobTest {
  private val csusHoldingsService = mockk<CsusHoldingsService>()
  private val etfHoldingService = mockk<EtfHoldingService>()
  private val etfBreakdownService = mockk<EtfBreakdownService>(relaxed = true)
  private val clock = Clock.fixed(LocalDate.of(2026, 6, 13).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))
  private val job = CsusHoldingsRetrievalJob(csusHoldingsService, etfHoldingService, etfBreakdownService, clock)

  private val today = LocalDate.of(2026, 6, 13)
  private val symbol = "GB00B0ZDNB53:GBP"

  @Test
  fun `should save fetched holdings against aviva symbol when none exist for today`() {
    val holdings = listOf(HoldingData(name = "NVIDIA CORP", ticker = "NVDA", sector = "Information Technology", weight = BigDecimal("7.42"), rank = 1))
    every { etfHoldingService.hasHoldingsForDate(symbol, today) } returns false
    every { csusHoldingsService.fetchHoldings() } returns holdings
    every { etfHoldingService.saveHoldings(symbol, today, holdings) } just Runs

    job.execute()

    verify(exactly = 1) { etfHoldingService.saveHoldings(symbol, today, holdings) }
  }

  @Test
  fun `should skip fetch when holdings already exist for today`() {
    every { etfHoldingService.hasHoldingsForDate(symbol, today) } returns true

    job.execute()

    verify(exactly = 0) { csusHoldingsService.fetchHoldings() }
    verify(exactly = 0) { etfHoldingService.saveHoldings(any(), any(), any()) }
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "ee.tenman.portfolio.job.CsusHoldingsRetrievalJobTest"`
Expected: FAIL — `CsusHoldingsRetrievalJob` does not exist yet (compilation error).

- [ ] **Step 3: Implement the job**

`src/main/kotlin/ee/tenman/portfolio/job/CsusHoldingsRetrievalJob.kt`:

```kotlin
package ee.tenman.portfolio.job

import ee.tenman.portfolio.blackrock.CsusHoldingsService
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@ScheduledJob
class CsusHoldingsRetrievalJob(
  private val csusHoldingsService: CsusHoldingsService,
  private val etfHoldingService: EtfHoldingService,
  private val etfBreakdownService: EtfBreakdownService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)
  private val jobTransactionService: JobTransactionService? = null

  @Scheduled(initialDelay = 20000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "0 50 23 * * ?")
  fun runJob() {
    val startTime = Instant.now(clock)
    var status = JobStatus.SUCCESS
    var message: String? = null
    try {
      message = execute().let { "CSUS holdings retrieval completed" }
      etfBreakdownService.evictBreakdownCache()
    } catch (e: Exception) {
      status = JobStatus.FAILURE
      message = "CSUS holdings job failed: ${e.message}"
      log.error("CSUS holdings job failed", e)
    } finally {
      jobTransactionService?.saveJobExecution(this, startTime, Instant.now(clock), status, message)
    }
  }

  override fun execute() {
    val today = LocalDate.now(clock)
    if (etfHoldingService.hasHoldingsForDate(AVIVA_SYMBOL, today)) {
      log.info("CSUS holdings already exist for $today, skipping")
      return
    }
    val holdings = csusHoldingsService.fetchHoldings()
    if (holdings.isEmpty()) {
      log.warn("No CSUS holdings fetched for $today")
      return
    }
    etfHoldingService.saveHoldings(AVIVA_SYMBOL, today, holdings)
    log.info("Saved ${holdings.size} CSUS holdings for Aviva pension on $today")
  }

  companion object {
    private const val AVIVA_SYMBOL = "GB00B0ZDNB53:GBP"
  }
}
```

**Important:** The test constructs the job with four constructor args `(csusHoldingsService, etfHoldingService, etfBreakdownService, clock)` — `JobTransactionService` is NOT a constructor param in this minimal version. But the production job needs it for `JobExecution` tracking. Reconcile by making `JobTransactionService` a constructor parameter and updating the test to pass a relaxed mock. Replace the field line `private val jobTransactionService: JobTransactionService? = null` with a constructor param, and the `finally` call to use it directly:

Final job constructor:

```kotlin
@ScheduledJob
class CsusHoldingsRetrievalJob(
  private val jobTransactionService: JobTransactionService,
  private val csusHoldingsService: CsusHoldingsService,
  private val etfHoldingService: EtfHoldingService,
  private val etfBreakdownService: EtfBreakdownService,
  private val clock: Clock,
) : Job {
```

and `finally`:

```kotlin
    } finally {
      jobTransactionService.saveJobExecution(this, startTime, Instant.now(clock), status, message)
    }
```

Update the test's job construction to:

```kotlin
  private val jobTransactionService = mockk<ee.tenman.portfolio.service.infrastructure.JobTransactionService>(relaxed = true)
  private val job = CsusHoldingsRetrievalJob(jobTransactionService, csusHoldingsService, etfHoldingService, etfBreakdownService, clock)
```

(The two `execute()`-driven tests do not touch `jobTransactionService`, but it must be a valid relaxed mock so construction succeeds. `execute()` is tested directly to avoid the `@Scheduled` wrapper and `Instant.now` paths.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "ee.tenman.portfolio.job.CsusHoldingsRetrievalJobTest"`
Expected: PASS — save path calls `saveHoldings` once; skip path never fetches or saves.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ee/tenman/portfolio/job/CsusHoldingsRetrievalJob.kt src/test/kotlin/ee/tenman/portfolio/job/CsusHoldingsRetrievalJobTest.kt
git commit -m "Add CSUS holdings retrieval job for Aviva pension"
```

---

## Task 8: Verify CI gates and full conversion/ECB/blackrock test scope

**Files:** none (verification only)

- [ ] **Step 1: Run the touched test scope**

Run:
```bash
./gradlew test --tests "ee.tenman.portfolio.ft.HistoricalPricesServiceTest" \
  --tests "ee.tenman.portfolio.ecb.EcbCsvParserTest" \
  --tests "ee.tenman.portfolio.blackrock.*" \
  --tests "ee.tenman.portfolio.job.CsusHoldingsRetrievalJobTest" \
  --tests "ee.tenman.portfolio.service.currency.CurrencyConversionServiceTest"
```
Expected: all PASS.

- [ ] **Step 2: Run the integration migration guard (requires Docker/Testcontainers)**

Run: `./gradlew test --tests "ee.tenman.portfolio.repository.AvivaPensionMigrationIT"`
Expected: PASS.

- [ ] **Step 3: Reproduce the real CI lint/static-analysis gate**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL. Fix any ktlint/detekt findings in the new files (no comments, no blank lines in method bodies, import ordering).

- [ ] **Step 4: Final commit if lint auto-formatted anything**

```bash
git add -A
git commit -m "Apply ktlint formatting to Aviva CSUS feed" || echo "nothing to format"
```

---

## Self-Review

**Spec coverage:**
- Spec Unit 1 (pin GBP-unit + migration guard) → Tasks 1, 2. ✓
- Spec Unit 2 (ECB fail-loud) → Task 3. ✓
- Spec Unit 3 (CSUS attach-to-Aviva: client + parser + service + job) → Tasks 4, 5, 6, 7. ✓
- Spec "no config/allowlist needed; FT-provider auto-included" → relied on in Task 7 (saves against `GB00B0ZDNB53:GBP`, no config touched). ✓
- Spec "fee model unchanged" → no task touches the fee SELLs except the read-only guard in Task 2. ✓
- CI gate `ktlintCheck detekt` → Task 8. ✓

**Type consistency:**
- `BlackRockHolding(ticker, name, sector, weight)` defined in Task 4, consumed identically in Task 6. ✓
- `HoldingData(name, ticker, sector, weight, rank, ...)` matches the real DTO (`dto/HoldingData.kt`). ✓
- `BlackRockHoldingsClient.getHoldingsCsv(productId, fileName, fileType, dataType)` defined in Task 5, called with `(PRODUCT_ID, "${FUND}_holdings", "csv", "fund")` in Task 6. ✓
- `CsusHoldingsService.fetchHoldings(): List<HoldingData>` defined in Task 6, called in Task 7. ✓
- `CsusHoldingsRetrievalJob` final constructor `(jobTransactionService, csusHoldingsService, etfHoldingService, etfBreakdownService, clock)` — Task 7 Step 3 reconciles the test to this signature. ✓
- `etfHoldingService.hasHoldingsForDate(String, LocalDate)` and `saveHoldings(String, LocalDate, List<HoldingData>)` match `EtfHoldingService`. ✓

**Placeholder scan:** No TBD/TODO; every code step shows complete code; commands have expected output. Task 7 intentionally shows the minimal-then-reconciled constructor to teach the `JobTransactionService` wiring — both forms are fully written, not deferred.
