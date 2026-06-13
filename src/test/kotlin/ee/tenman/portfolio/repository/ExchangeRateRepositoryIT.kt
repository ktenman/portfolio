package ee.tenman.portfolio.repository

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.job.TransactionRunner
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class ExchangeRateRepositoryIT {
  @Resource
  private lateinit var exchangeRateRepository: ExchangeRateRepository

  @Resource
  private lateinit var transactionRunner: TransactionRunner

  private val friday = LocalDate.of(2026, 6, 5)

  private fun upsert(
    date: LocalDate,
    rate: String,
  ) = transactionRunner.runInTransaction {
    exchangeRateRepository.upsert(date, Currency.EUR.name, Currency.GBP.name, BigDecimal(rate))
  }

  @Test
  fun `should insert rate when upserting new date`() {
    upsert(friday, "0.86433")

    val saved =
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndEntryDateLessThanEqualOrderByEntryDateDesc(
        Currency.EUR,
        Currency.GBP,
        friday,
      )

    expect(saved?.rate).notToEqualNull().toEqualNumerically(BigDecimal("0.86433"))
  }

  @Test
  fun `should update rate when upserting existing date`() {
    upsert(friday, "0.86433")
    upsert(friday, "0.87001")

    val all = exchangeRateRepository.findAll()

    expect(all).toHaveSize(1)
    expect(all.first().rate).toEqualNumerically(BigDecimal("0.87001"))
  }

  @Test
  fun `should find most recent rate at or before date when exact date is missing`() {
    upsert(friday.minusDays(1), "0.86001")
    upsert(friday, "0.86433")

    val sunday = friday.plusDays(2)
    val found =
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndEntryDateLessThanEqualOrderByEntryDateDesc(
        Currency.EUR,
        Currency.GBP,
        sunday,
      )

    expect(found?.rate).notToEqualNull().toEqualNumerically(BigDecimal("0.86433"))
  }

  @Test
  fun `should return null when no rate exists at or before date`() {
    upsert(friday, "0.86433")

    val found =
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndEntryDateLessThanEqualOrderByEntryDateDesc(
        Currency.EUR,
        Currency.GBP,
        friday.minusDays(1),
      )

    expect(found).toEqual(null)
  }

  @Test
  fun `should find latest entry date`() {
    upsert(friday.minusDays(7), "0.85900")
    upsert(friday, "0.86433")

    val latest =
      exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByEntryDateDesc(
        Currency.EUR,
        Currency.GBP,
      )

    expect(latest?.entryDate).toEqual(friday)
  }

  @Test
  fun `should find rates within date range`() {
    upsert(friday.minusDays(10), "0.85800")
    upsert(friday.minusDays(1), "0.86001")
    upsert(friday, "0.86433")

    val rates =
      exchangeRateRepository.findAllByBaseCurrencyAndQuoteCurrencyAndEntryDateBetween(
        Currency.EUR,
        Currency.GBP,
        friday.minusDays(5),
        friday,
      )

    expect(rates).toHaveSize(2)
  }
}
