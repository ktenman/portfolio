package ee.tenman.portfolio.service.currency

import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.common.DailyPriceDataImpl
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.repository.ExchangeRateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.TreeMap

@Service
class CurrencyConversionService(
  private val exchangeRateRepository: ExchangeRateRepository,
) {
  companion object {
    private const val SCALE = 10
    private const val RATE_LOOKBACK_DAYS = 10L
  }

  @Transactional(readOnly = true)
  fun convertDailyPricesToEur(
    prices: Map<LocalDate, DailyPriceData>,
    currency: Currency,
  ): Map<LocalDate, DailyPriceData> {
    if (currency == Currency.EUR || prices.isEmpty()) return prices
    val rates = loadRates(currency, prices.keys.min(), prices.keys.max())
    return prices.mapValues { (date, data) -> convert(data, findRate(rates, date, currency)) }
  }

  @Transactional(readOnly = true)
  fun convertToEur(
    amount: BigDecimal,
    currency: Currency,
    date: LocalDate,
  ): BigDecimal {
    if (currency == Currency.EUR) return amount
    return amount.divide(rateOn(currency, date), SCALE, RoundingMode.HALF_UP)
  }

  private fun loadRates(
    currency: Currency,
    startDate: LocalDate,
    endDate: LocalDate,
  ): TreeMap<LocalDate, BigDecimal> {
    val loaded =
      exchangeRateRepository.findAllByBaseCurrencyAndQuoteCurrencyAndEntryDateBetween(
        Currency.EUR,
        currency,
        startDate.minusDays(RATE_LOOKBACK_DAYS),
        endDate,
      )
    return TreeMap(loaded.associate { it.entryDate to it.rate })
  }

  private fun findRate(
    rates: TreeMap<LocalDate, BigDecimal>,
    date: LocalDate,
    currency: Currency,
  ): BigDecimal = rates.floorEntry(date)?.value ?: rateOn(currency, date)

  private fun rateOn(
    currency: Currency,
    date: LocalDate,
  ): BigDecimal =
    exchangeRateRepository
      .findFirstByBaseCurrencyAndQuoteCurrencyAndEntryDateLessThanEqualOrderByEntryDateDesc(Currency.EUR, currency, date)
      ?.rate
      ?: throw IllegalStateException("No exchange rate found for $currency on or before $date")

  private fun convert(
    data: DailyPriceData,
    rate: BigDecimal,
  ): DailyPriceData =
    DailyPriceDataImpl(
      open = data.open.divide(rate, SCALE, RoundingMode.HALF_UP),
      high = data.high.divide(rate, SCALE, RoundingMode.HALF_UP),
      low = data.low.divide(rate, SCALE, RoundingMode.HALF_UP),
      close = data.close.divide(rate, SCALE, RoundingMode.HALF_UP),
      volume = data.volume,
    )
}
