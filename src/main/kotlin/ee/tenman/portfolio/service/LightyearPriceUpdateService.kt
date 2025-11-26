package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class LightyearPriceUpdateService(
  private val instrumentService: InstrumentService,
  private val dailyPriceService: DailyPriceService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun processSymbol(
    symbol: String,
    price: BigDecimal,
    isWeekend: Boolean,
    today: LocalDate,
  ): ProcessResult =
    runCatching { updateInstrumentPrice(symbol, price, isWeekend, today) }
      .onFailure { log.warn("Failed to update price for symbol $symbol: ${it.message}") }
      .getOrDefault(ProcessResult.FAILED)

  private fun updateInstrumentPrice(
    symbol: String,
    price: BigDecimal,
    isWeekend: Boolean,
    today: LocalDate,
  ): ProcessResult {
    val instrument = instrumentService.findBySymbol(symbol)
    if (instrument == null) {
      log.warn("Instrument not found for symbol: $symbol")
      return ProcessResult.FAILED
    }

    saveCurrentPrice(instrument, price, symbol)

    if (isWeekend) return ProcessResult.SUCCESS_WITHOUT_DAILY_PRICE

    saveDailyPrice(instrument, price, today, symbol)
    return ProcessResult.SUCCESS_WITH_DAILY_PRICE
  }

  private fun saveCurrentPrice(
    instrument: Instrument,
    price: BigDecimal,
    symbol: String,
  ) {
    instrument.currentPrice = price
    instrumentService.save(instrument)
    log.debug("Updated current price for {}: {}", symbol, price)
  }

  private fun saveDailyPrice(
    instrument: Instrument,
    price: BigDecimal,
    today: LocalDate,
    symbol: String,
  ) {
    val dailyPrice =
      DailyPrice(
      instrument = instrument,
      entryDate = today,
      providerName = ProviderName.LIGHTYEAR,
      openPrice = price,
      highPrice = price,
      lowPrice = price,
      closePrice = price,
      volume = null,
    )
    dailyPriceService.saveDailyPrice(dailyPrice)
    log.debug("Saved Lightyear daily price for {}: {}", symbol, price)
  }
}
