package ee.tenman.portfolio.job

import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.DailyPriceService
import ee.tenman.portfolio.service.InstrumentService
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
      dailyData.forEach { (date, data) -> dailyPriceService.saveDailyPrice(create(instrument, date, data, providerName)) }
    }
    update(instrument)
    log.info("Successfully retrieved and processed data for ${instrument.symbol}")
  }

  private fun create(
    instrument: Instrument,
    date: LocalDate,
    data: DailyPriceData,
    providerName: ProviderName,
  ) = DailyPrice(
      instrument = instrument,
      entryDate = date,
      providerName = providerName,
      openPrice = data.open,
      highPrice = data.high,
      lowPrice = data.low,
      closePrice = data.close,
      volume = data.volume,
    )

  private fun update(instrument: Instrument) {
    val price = dailyPriceService.findLastDailyPrice(instrument, LocalDate.now(clock))
    instrument.currentPrice = price?.closePrice
    instrumentService.save(instrument)
  }
}
