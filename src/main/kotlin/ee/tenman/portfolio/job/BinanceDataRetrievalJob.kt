package ee.tenman.portfolio.job

import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.util.DataProcessingUtil
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BinanceDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val jobExecutionService: JobExecutionService,
  private val binanceService: BinanceService,
  private val dataProcessingUtil: DataProcessingUtil
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 */5 * * * *")
  fun runJob() {
    log.info("Running Binance data retrieval job")
    jobExecutionService.executeJob(this)
    log.info("Completed Binance data retrieval job")
  }

  override fun execute() {
    log.info("Starting Binance data retrieval job")
    val instruments = instrumentService.getAllInstruments().filter { it.providerName == ProviderName.BINANCE }

    instruments.forEach { instrument ->
      log.info("Retrieving data for instrument: ${instrument.symbol}")
      val dailyData = binanceService.getDailyPrices(instrument.symbol)
      dataProcessingUtil.processDailyData(instrument, dailyData)
    }

    log.info("Completed Binance data retrieval job. Processed ${instruments.size} instruments.")
  }

  override fun getName(): String = "BinanceDataRetrievalJob"

}
