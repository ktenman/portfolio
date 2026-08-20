package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.PortfolioDailySummary
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.time.LocalDate

class CalculationServiceBatchXirrTest : CalculationServiceTestBase() {
  private fun stubSummaryFor(date: LocalDate): PortfolioDailySummary =
    PortfolioDailySummary(
      entryDate = date,
      totalValue = BigDecimal("10000"),
      xirrAnnualReturn = BigDecimal("0.15"),
      totalProfit = BigDecimal("2000"),
      earningsPerDay = BigDecimal("5.48"),
    ).also {
      every { portfolioSummaryService.calculateSummaryForDate(date) } returns it
      every { portfolioSummaryService.saveDailySummary(it) } returns it
    }

  @Test
  fun `should calculateBatchXirrAsync processes single date successfully`() {
    runBlocking {
      val dates = listOf(today)
      val summary = stubSummaryFor(today)

      val result = calculationService.calculateBatchXirrAsync(dates)

      expect(result.processedDates).toEqual(1)
      expect(result.failedCalculations).toBeEmpty()
      verify { portfolioSummaryService.calculateSummaryForDate(today) }
      verify { portfolioSummaryService.saveDailySummary(summary) }
    }
  }

  @Test
  fun `should calculateBatchXirrAsync processes multiple dates successfully`() {
    runBlocking {
      val dates = listOf(today, today.plusDays(1), today.plusDays(2))

      dates.forEach { stubSummaryFor(it) }

      val result = calculationService.calculateBatchXirrAsync(dates)

      expect(result.processedDates).toEqual(3)
      expect(result.processedInstruments).toEqual(0)
      expect(result.failedCalculations).toBeEmpty()
      verify(exactly = 3) { portfolioSummaryService.calculateSummaryForDate(any()) }
      verify(exactly = 3) { portfolioSummaryService.saveDailySummary(any()) }
    }
  }

  @Test
  fun `should calculateBatchXirrAsync handles empty date list`() {
    runBlocking {
      val result = calculationService.calculateBatchXirrAsync(emptyList())

      expect(result.processedDates).toEqual(0)
      expect(result.failedCalculations).toBeEmpty()
    }
  }

  @Test
  fun `should calculateMedian returns 0 when list is empty`() {
    val result = calculationService.calculateMedian(emptyList())

    expect(result).toEqual(0.0)
  }

  @ParameterizedTest
  @CsvSource(
    "42.5, 42.5",
    "10.0 20.0, 15.0",
    "5.0 10.0 15.0 20.0 25.0, 15.0",
    "5.0 10.0 20.0 25.0, 15.0",
    "25.0 5.0 15.0 10.0 20.0, 15.0",
  )
  fun `should calculateMedian returns the middle value of the sorted values`(
    values: String,
    expected: Double,
  ) {
    val result = calculationService.calculateMedian(values.split(" ").map { it.toDouble() })

    expect(result).toEqual(expected)
  }

  @Test
  fun `should calculateBatchXirrAsync handles all failures correctly`() {
    runBlocking {
      val dates = listOf(today, today.plusDays(1), today.plusDays(2))

      dates.forEach { date ->
        every { portfolioSummaryService.calculateSummaryForDate(date) } throws RuntimeException("Calculation failed for $date")
      }

      val result = calculationService.calculateBatchXirrAsync(dates)

      expect(result.processedDates).toEqual(0)
      expect(result.failedCalculations).toHaveSize(3)
      result.failedCalculations.forEach { expect(it).toContain("Failed for date") }
    }
  }

  @Test
  fun `should calculateBatchXirrAsync calculates duration correctly`() {
    runBlocking {
      val dates = listOf(today)
      stubSummaryFor(today)

      val startTime = System.currentTimeMillis()
      val result = calculationService.calculateBatchXirrAsync(dates)
      val endTime = System.currentTimeMillis()

      expect(result.duration).toBeGreaterThanOrEqualTo(0)
      expect(result.duration).toBeLessThanOrEqualTo(endTime - startTime + 100)
    }
  }

  @Test
  fun `should calculateBatchXirrAsync processes mixed success and failure scenarios`() {
    runBlocking {
      val date1 = today
      val date2 = today.plusDays(1)
      val date3 = today.plusDays(2)
      val dates = listOf(date1, date2, date3)

      val summary1 = stubSummaryFor(date1)
      val summary3 = stubSummaryFor(date3)
      every { portfolioSummaryService.calculateSummaryForDate(date2) } throws RuntimeException("Failed for date2")

      val result = calculationService.calculateBatchXirrAsync(dates)

      expect(result.processedDates).toEqual(2)
      expect(result.failedCalculations).toHaveSize(1)
      expect(result.failedCalculations[0]).toContain("Failed for date $date2")
      verify { portfolioSummaryService.saveDailySummary(summary1) }
      verify { portfolioSummaryService.saveDailySummary(summary3) }
    }
  }
}
