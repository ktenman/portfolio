package ee.tenman.portfolio.service.currency

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.ExchangeRate
import ee.tenman.portfolio.ecb.EcbDailyRate
import ee.tenman.portfolio.repository.ExchangeRateRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ExchangeRateServiceTest {
  private val exchangeRateRepository = mockk<ExchangeRateRepository>()
  private val exchangeRateService = ExchangeRateService(exchangeRateRepository)

  @Test
  fun `should upsert each rate with eur base and given quote currency`() {
    every { exchangeRateRepository.upsert(any(), any(), any(), any()) } just runs
    val rates =
      listOf(
        EcbDailyRate(LocalDate.of(2026, 6, 9), BigDecimal("0.8634")),
        EcbDailyRate(LocalDate.of(2026, 6, 10), BigDecimal("0.86228")),
      )

    exchangeRateService.saveRates(Currency.GBP, rates)

    verify { exchangeRateRepository.upsert(LocalDate.of(2026, 6, 9), "EUR", "GBP", BigDecimal("0.8634")) }
    verify { exchangeRateRepository.upsert(LocalDate.of(2026, 6, 10), "EUR", "GBP", BigDecimal("0.86228")) }
  }

  @Test
  fun `should return latest stored rate date`() {
    every {
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByEntryDateDesc(Currency.EUR, Currency.GBP)
    } returns ExchangeRate(LocalDate.of(2026, 6, 10), Currency.EUR, Currency.GBP, BigDecimal("0.86228"))

    val latest = exchangeRateService.findLatestRateDate(Currency.GBP)

    expect(latest).toEqual(LocalDate.of(2026, 6, 10))
  }

  @Test
  fun `should return null latest date when no rates are stored`() {
    every {
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByEntryDateDesc(Currency.EUR, Currency.GBP)
    } returns null

    val latest = exchangeRateService.findLatestRateDate(Currency.GBP)

    expect(latest).toEqual(null)
  }
}
