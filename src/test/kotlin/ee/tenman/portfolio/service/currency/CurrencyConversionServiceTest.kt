package ee.tenman.portfolio.service.currency

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.common.DailyPriceDataImpl
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.ExchangeRate
import ee.tenman.portfolio.repository.ExchangeRateRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class CurrencyConversionServiceTest {
  private val exchangeRateRepository = mockk<ExchangeRateRepository>()
  private val currencyConversionService = CurrencyConversionService(exchangeRateRepository)

  private val tuesday = LocalDate.of(2026, 6, 9)
  private val wednesday = LocalDate.of(2026, 6, 10)

  private fun priceData(close: String): DailyPriceData =
    DailyPriceDataImpl(
      open = BigDecimal(close).subtract(BigDecimal("0.04")),
      high = BigDecimal(close).add(BigDecimal("0.04")),
      low = BigDecimal(close).subtract(BigDecimal("0.08")),
      close = BigDecimal(close),
      volume = 1234,
    )

  private fun rate(
    date: LocalDate,
    value: String,
  ): ExchangeRate = ExchangeRate(date, Currency.EUR, Currency.GBP, BigDecimal(value))

  @Test
  fun `should return prices unchanged when currency is eur`() {
    val prices = mapOf(tuesday to priceData("11.74"))

    val converted = currencyConversionService.convertDailyPricesToEur(prices, Currency.EUR)

    expect(converted).toEqual(prices)
    verify(exactly = 0) { exchangeRateRepository.findAllByBaseCurrencyAndQuoteCurrencyAndEntryDateBetween(any(), any(), any(), any()) }
  }

  @Test
  fun `should divide all price fields by same day rate when converting gbp to eur`() {
    every {
      exchangeRateRepository.findAllByBaseCurrencyAndQuoteCurrencyAndEntryDateBetween(Currency.EUR, Currency.GBP, any(), any())
    } returns listOf(rate(tuesday, "0.8"))
    val prices = mapOf(tuesday to priceData("11.74"))

    val converted = currencyConversionService.convertDailyPricesToEur(prices, Currency.GBP)

    val data = converted[tuesday]
    expect(data?.close).notToEqualNull().toEqualNumerically(BigDecimal("14.675"))
    expect(data?.open).notToEqualNull().toEqualNumerically(BigDecimal("14.625"))
    expect(data?.high).notToEqualNull().toEqualNumerically(BigDecimal("14.725"))
    expect(data?.low).notToEqualNull().toEqualNumerically(BigDecimal("14.575"))
    expect(data?.volume).toEqual(1234)
  }

  @Test
  fun `should use most recent prior rate when date has no rate`() {
    every {
      exchangeRateRepository.findAllByBaseCurrencyAndQuoteCurrencyAndEntryDateBetween(Currency.EUR, Currency.GBP, any(), any())
    } returns listOf(rate(tuesday, "0.5"))
    val prices = mapOf(wednesday to priceData("11.74"))

    val converted = currencyConversionService.convertDailyPricesToEur(prices, Currency.GBP)

    expect(converted[wednesday]?.close).notToEqualNull().toEqualNumerically(BigDecimal("23.48"))
  }

  @Test
  fun `should throw when no rate exists at or before a price date`() {
    every {
      exchangeRateRepository.findAllByBaseCurrencyAndQuoteCurrencyAndEntryDateBetween(Currency.EUR, Currency.GBP, any(), any())
    } returns emptyList()
    every {
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndEntryDateLessThanEqualOrderByEntryDateDesc(
        Currency.EUR,
        Currency.GBP,
        tuesday,
      )
    } returns null
    val prices = mapOf(tuesday to priceData("11.74"))

    expect {
      currencyConversionService.convertDailyPricesToEur(prices, Currency.GBP)
    }.toThrow<IllegalStateException>().messageToContain("GBP")
  }

  @Test
  fun `should return amount unchanged when converting eur to eur for a date`() {
    val converted = currencyConversionService.convertToEur(BigDecimal("11.74"), Currency.EUR, tuesday)

    expect(converted).toEqualNumerically(BigDecimal("11.74"))
    verify(exactly = 0) {
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndEntryDateLessThanEqualOrderByEntryDateDesc(
        any(),
        any(),
        any(),
      )
    }
  }

  @Test
  fun `should divide amount by rate effective on or before the date when converting gbp to eur`() {
    every {
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndEntryDateLessThanEqualOrderByEntryDateDesc(
        Currency.EUR,
        Currency.GBP,
        wednesday,
      )
    } returns rate(tuesday, "0.85")

    val converted = currencyConversionService.convertToEur(BigDecimal("11.74"), Currency.GBP, wednesday)

    expect(converted).toEqualNumerically(BigDecimal("13.8117647059"))
  }

  @Test
  fun `should throw when converting an amount and no rate exists on or before the date`() {
    every {
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndEntryDateLessThanEqualOrderByEntryDateDesc(
        Currency.EUR,
        Currency.GBP,
        tuesday,
      )
    } returns null

    expect {
      currencyConversionService.convertToEur(BigDecimal("11.74"), Currency.GBP, tuesday)
    }.toThrow<IllegalStateException>().messageToContain("GBP")
  }

  @Test
  fun `should fall back to repository lookup when rate is older than loaded range`() {
    every {
      exchangeRateRepository.findAllByBaseCurrencyAndQuoteCurrencyAndEntryDateBetween(Currency.EUR, Currency.GBP, any(), any())
    } returns emptyList()
    every {
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndEntryDateLessThanEqualOrderByEntryDateDesc(
        Currency.EUR,
        Currency.GBP,
        tuesday,
      )
    } returns rate(tuesday.minusMonths(1), "0.5")
    val prices = mapOf(tuesday to priceData("11.74"))

    val converted = currencyConversionService.convertDailyPricesToEur(prices, Currency.GBP)

    expect(converted[tuesday]?.close).notToEqualNull().toEqualNumerically(BigDecimal("23.48"))
  }
}
