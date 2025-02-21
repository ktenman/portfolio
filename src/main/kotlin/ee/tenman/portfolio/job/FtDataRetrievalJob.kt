package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.ft.HistoricalPricesService
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.util.DataProcessingUtil
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class FtDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val historicalPricesService: HistoricalPricesService,
  private val dataProcessingUtil: DataProcessingUtil,
  private val jobExecutionService: JobExecutionService
) : Job {

  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 0 0/2 * * *")
  fun runJob() {
    log.info("Running FT data retrieval job")
    jobExecutionService.executeJob(this)
    log.info("Completed FT data retrieval job")
  }

  override fun execute() {
    log.info("Starting FT data retrieval execution")
    val instruments = instrumentService.getAllInstruments()
      .filter { it.providerName == ProviderName.FT }

    instruments.forEach { instrument ->
      log.info("Retrieving FT data for instrument: ${instrument.symbol}")
      val ftData = historicalPricesService.fetchPrices(instrument.symbol)
      if (ftData.isNotEmpty()) {
        dataProcessingUtil.processDailyData(instrument, ftData, ProviderName.FT)
      } else {
        log.warn("No FT data found for instrument: ${instrument.symbol}")
      }
    }

    log.info("Completed FT data retrieval execution. Processed ${instruments.size} instruments.")
  }
}
