package ee.tenman.portfolio.service.currency

import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.ecb.EcbDailyRate
import ee.tenman.portfolio.repository.ExchangeRateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class ExchangeRateService(
  private val exchangeRateRepository: ExchangeRateRepository,
) {
  @Transactional
  fun saveRates(
    quoteCurrency: Currency,
    rates: List<EcbDailyRate>,
  ) {
    rates.forEach { exchangeRateRepository.upsert(it.date, Currency.EUR.name, quoteCurrency.name, it.rate) }
  }

  @Transactional(readOnly = true)
  fun findLatestRateDate(quoteCurrency: Currency): LocalDate? =
    exchangeRateRepository
      .findFirstByBaseCurrencyAndQuoteCurrencyOrderByEntryDateDesc(Currency.EUR, quoteCurrency)
      ?.entryDate
}
