package ee.tenman.portfolio.job

import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class DataProcessingUtil(
  private val dailyPriceService: DailyPriceService,
  private val instrumentService: InstrumentService,
  private val transactionRunner: TransactionRunner,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun processDailyData(
    instrument: Instrument,
    dailyData: Map<LocalDate, DailyPriceData>,
    providerName: ProviderName,
  ) {
    transactionRunner.runInTransaction {
      dailyData.forEach { (date, data) ->
        val dailyPrice = createDailyPrice(instrument, date, data, providerName)
        dailyPriceService.saveDailyPrice(dailyPrice)
      }
    }

    updateInstrumentPrice(instrument)
    log.info("Successfully retrieved and processed data for ${instrument.symbol}")
  }

  private fun createDailyPrice(
    instrument: Instrument,
    date: LocalDate,
    data: DailyPriceData,
    providerName: ProviderName,
  ): DailyPrice =
    DailyPrice(
      instrument = instrument,
      entryDate = date,
      providerName = providerName,
      openPrice = data.open,
      highPrice = data.high,
      lowPrice = data.low,
      closePrice = data.close,
      volume = data.volume,
    )

  private fun updateInstrumentPrice(instrument: Instrument) {
    val currentDate = LocalDate.now(clock)
    val dailyPrice = dailyPriceService.findLastDailyPrice(instrument, currentDate)
    instrumentService.updateCurrentPrice(instrument.id, dailyPrice?.closePrice)
  }
}
