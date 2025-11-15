package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.ProcessResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class Trading212PriceUpdateService(
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
    try {
      val instrument = instrumentService.findBySymbol(symbol)
      if (instrument != null) {
        instrument.currentPrice = price
        instrumentService.saveInstrument(instrument)
        log.debug("Updated current price for {}: {}", symbol, price)

        if (!isWeekend) {
          val dailyPrice =
            DailyPrice(
              instrument = instrument,
              entryDate = today,
              providerName = ProviderName.TRADING212,
              openPrice = price,
              highPrice = price,
              lowPrice = price,
              closePrice = price,
              volume = null,
            )
          dailyPriceService.saveDailyPrice(dailyPrice)
          log.debug("Saved Trading212 daily price for {}: {}", symbol, price)
          ProcessResult.SUCCESS_WITH_DAILY_PRICE
        } else {
          ProcessResult.SUCCESS_WITHOUT_DAILY_PRICE
        }
      } else {
        log.warn("Instrument not found for symbol: $symbol")
        ProcessResult.FAILED
      }
    } catch (e: Exception) {
      log.warn("Failed to update price for symbol $symbol: ${e.message}")
      ProcessResult.FAILED
    }
}
