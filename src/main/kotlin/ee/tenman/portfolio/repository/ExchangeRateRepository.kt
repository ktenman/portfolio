package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.ExchangeRate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface ExchangeRateRepository : JpaRepository<ExchangeRate, Long> {
  fun findFirstByBaseCurrencyAndQuoteCurrencyAndEntryDateLessThanEqualOrderByEntryDateDesc(
    baseCurrency: Currency,
    quoteCurrency: Currency,
    entryDate: LocalDate,
  ): ExchangeRate?

  fun findFirstByBaseCurrencyAndQuoteCurrencyOrderByEntryDateDesc(
    baseCurrency: Currency,
    quoteCurrency: Currency,
  ): ExchangeRate?

  fun findAllByBaseCurrencyAndQuoteCurrencyAndEntryDateBetween(
    baseCurrency: Currency,
    quoteCurrency: Currency,
    startDate: LocalDate,
    endDate: LocalDate,
  ): List<ExchangeRate>

  @Modifying
  @Query(
    """
    INSERT INTO exchange_rate (entry_date, base_currency, quote_currency, rate, created_at, updated_at, version)
    VALUES (:entryDate, :baseCurrency, :quoteCurrency, :rate, NOW(), NOW(), 0)
    ON CONFLICT (entry_date, base_currency, quote_currency)
    DO UPDATE SET rate = :rate, updated_at = NOW(), version = exchange_rate.version + 1
    """,
    nativeQuery = true,
  )
  fun upsert(
    entryDate: LocalDate,
    baseCurrency: String,
    quoteCurrency: String,
    rate: BigDecimal,
  )
}
