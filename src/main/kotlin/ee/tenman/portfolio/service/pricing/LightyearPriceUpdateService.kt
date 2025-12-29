package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.ProcessResult
import ee.tenman.portfolio.service.instrument.InstrumentService
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
    runCatching {
      val instrument = instrumentService.findBySymbol(symbol)
      updateInstrumentPrice(instrument, price, symbol)
      if (isWeekend) return@runCatching ProcessResult.SUCCESS_WITHOUT_DAILY_PRICE
      saveDailyPrice(instrument, price, today, symbol)
      ProcessResult.SUCCESS_WITH_DAILY_PRICE
    }.getOrElse {
      log.warn("Failed to update price for symbol $symbol: ${it.message}")
      ProcessResult.FAILED
    }

  private fun updateInstrumentPrice(
    instrument: Instrument,
    price: BigDecimal,
    symbol: String,
  ) {
    instrumentService.updateCurrentPrice(instrument.id, price)
    log.debug("Updated current price for $symbol: $price")
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
    log.debug("Saved Lightyear daily price for $symbol: $price")
  }
}
