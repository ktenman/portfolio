package ee.tenman.portfolio.service.calculation

import ch.tutteli.atrium.api.fluent.en_GB.notToBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.ProviderName
import io.mockk.every
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import kotlin.math.abs

class CalculationServiceTest : CalculationServiceTestBase() {
  private fun createDailyPrice(
    date: LocalDate,
    price: BigDecimal,
  ): DailyPrice =
    DailyPrice(
      instrument = testInstrument,
      entryDate = date,
      providerName = ProviderName.FT,
      openPrice = price,
      highPrice = price,
      lowPrice = price,
      closePrice = price,
      volume = 1000L,
    )

  private fun stubPrices(vararg prices: Pair<Int, Double>) {
    val dailyPrices =
      prices.map { (daysAgo, price) ->
        createDailyPrice(today.minusDays(daysAgo.toLong()), BigDecimal(price.toString()))
      }
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns dailyPrices
  }

  private fun stubTrend(
    startPrice: Double,
    endPrice: Double,
    startDate: LocalDate = today.minusDays(60),
  ) {
    val dates = listOf(startDate, startDate.plusDays(15), startDate.plusDays(30), startDate.plusDays(45), today)
    val prices =
      dates.mapIndexed { index, date ->
        createDailyPrice(date, BigDecimal(startPrice + ((endPrice - startPrice) * (index / 4.0))))
      }
    every { dataRetrievalService.findAllByInstrument(testInstrument) } returns prices
  }

  @Test
  fun `should return empty list when no daily prices exist`() {
    stubPrices()

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).toBeEmpty()
  }

  @Test
  fun `should return empty list when only one daily price exists`() {
    stubPrices(10 to 25.0)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).toBeEmpty()
  }

  @Test
  fun `should calculate a positive xirr for an upward price trend`() {
    stubTrend(startPrice = 25.0, endPrice = 30.0)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result[0].calculate()).toBeGreaterThan(0.0)
  }

  @Test
  fun `should calculate a negative xirr above minus one for a downward price trend`() {
    stubTrend(startPrice = 30.0, endPrice = 20.0)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result[0].calculate()).toBeGreaterThan(-1.0).toBeLessThan(0.0)
  }

  @Test
  fun `should calculate a near zero xirr for stable prices`() {
    stubTrend(startPrice = 100.0, endPrice = 100.0)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(abs(result[0].calculate())).toBeLessThan(0.0001)
  }

  @Test
  fun `should create multiple time windows at two-week intervals`() {
    stubTrend(startPrice = 20.0, endPrice = 35.0, startDate = today.minusMonths(6))

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).notToBeEmpty()

    val endDates =
      result
        .map { it.getCashFlows().maxOf { tx -> tx.date } }
        .distinct()
        .sorted()

    expect(endDates.size).toBeGreaterThan(1)
    expect(endDates[1].toEpochDay() - endDates[0].toEpochDay()).toEqual(15)
  }

  @Test
  fun `should keep xirr above minus one when the ending price is extreme`() {
    stubPrices(30 to 25.0, 20 to 1000000.0)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    result.forEach { xirr ->
      expect(xirr.calculate()).toBeGreaterThan(-1.0)
      expect(xirr.getCashFlows().last().amount).toBeGreaterThan(0.0)
    }
  }

  @Test
  fun `should cap xirr when the ending price is very low`() {
    stubPrices(30 to 25.0, 20 to 20.0, 10 to 0.01)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    result.forEach { xirr -> expect(xirr.calculate()).toBeLessThanOrEqualTo(0.99) }
  }

  @Test
  fun `should calculate a plausible xirr for market-like fluctuations`() {
    stubPrices(50 to 98.50, 40 to 102.30, 30 to 99.75, 20 to 105.40, 10 to 103.80)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).notToBeEmpty()
    expect(result[0].calculate()).toBeGreaterThan(-1.0).toBeLessThan(10.0)
  }

  @Test
  fun `should create buy and hold transactions`() {
    stubPrices(60 to 100.0, 40 to 100.0, 20 to 100.0, 10 to 100.0, 0 to 100.0)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).notToBeEmpty()

    val transactions = result[0].getCashFlows().sortedBy { it.date }
    expect(transactions).toHaveSize(2)
    expect(transactions[0].amount).toEqual(-1000.0)
    expect(transactions[1].amount).toEqual(1000.0)
  }

  @Test
  fun `should calculateRollingXirr breaks loop when filtering results in less than 2 prices`() {
    stubPrices(90 to 100.0, 60 to 110.0)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    expect(result).notToBeEmpty()
  }

  @Test
  fun `should calculateRollingXirr excludes xirr when currentValue is zero`() {
    stubPrices(60 to 100.0, 40 to 50.0, 20 to 0.0)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    result.forEach { xirr ->
      val lastTransaction = xirr.getCashFlows().last()
      expect(lastTransaction.amount).toBeGreaterThan(0.0)
    }
  }

  @Test
  fun `should calculateRollingXirr filters out xirr calculations that throw exceptions`() {
    stubPrices(60 to 0.0, 40 to 0.0, 20 to 100.0)

    val result = calculationService.calculateRollingXirr(instrumentCode)

    result.forEach { xirr ->
      expect(xirr.calculate()).toBeGreaterThan(-1.0)
    }
  }

  @Test
  fun `should getCalculationResult returns zeros when no valid xirr results exist`() {
    stubPrices(30 to 100.0, 20 to 0.0)

    val result = calculationService.getCalculationResult()

    expect(result.median).toEqual(0.0)
    expect(result.average).toEqual(0.0)
    expect(result.cashFlows).toBeEmpty()
  }

  @Test
  fun `should getCalculationResult filters out xirr values less than or equal to -1`() {
    stubPrices(60 to 1000.0, 40 to 500.0, 20 to 1.0)

    val result = calculationService.getCalculationResult()

    expect(result.cashFlows).notToBeEmpty()
    expect(result.cashFlows.minOf { it.amount }).toBeGreaterThan(-100.0)
  }

  @Test
  fun `should getCalculationResult uses calculation dispatcher for async operations`() {
    stubTrend(startPrice = 20.0, endPrice = 30.0)

    val result = calculationService.getCalculationResult()

    expect(result.median).toBeGreaterThan(0.0)
    expect(result.average).toBeGreaterThan(0.0)
    expect(result.cashFlows).notToBeEmpty()
  }

  @Test
  fun `should calculateRollingXirr throws exception when instrument not found`() {
    every { instrumentRepository.findBySymbol("UNKNOWN") } returns Optional.empty()

    val exception =
      assertThrows<RuntimeException> {
        calculationService.calculateRollingXirr("UNKNOWN")
      }

    expect(exception.message!!).toContain("Instrument not found with symbol: UNKNOWN")
  }
}
