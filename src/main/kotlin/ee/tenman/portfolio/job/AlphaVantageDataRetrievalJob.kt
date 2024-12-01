package ee.tenman.portfolio.job

import ee.tenman.portfolio.alphavantage.AlphaVantageService
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.util.DataProcessingUtil
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class AlphaVantageDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val alphaVantageService: AlphaVantageService,
  private val dataProcessingUtil: DataProcessingUtil,
  private val jobExecutionService: JobExecutionService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "0 0 0/2 * * *")
  fun runJob() {
    log.info("Running AlphaVantage data retrieval job")
    jobExecutionService.executeJob(this)
    log.info("Completed AlphaVantage data retrieval job")
  }

  override fun execute() {
    log.info("Starting AlphaVantage data retrieval job")
    val instruments = instrumentService.getAllInstruments().filter { it.providerName == ProviderName.ALPHA_VANTAGE }

    instruments.forEach { instrument ->
      log.info("Retrieving data for instrument: ${instrument.symbol}")
      val dailyData = alphaVantageService.getDailyTimeSeriesForLastWeek(instrument.symbol)
      dataProcessingUtil.processDailyData(instrument, dailyData)
    }

    log.info("Completed AlphaVantage data retrieval job. Processed ${instruments.size} instruments.")
  }
}
