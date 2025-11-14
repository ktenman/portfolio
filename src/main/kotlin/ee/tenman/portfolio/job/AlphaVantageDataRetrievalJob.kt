package ee.tenman.portfolio.job

import ee.tenman.portfolio.alphavantage.AlphaVantageService
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["scheduling.enabled"], havingValue = "true", matchIfMissing = true)
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
    log.info("Starting AlphaVantage data retrieval execution")
    val instruments =
      instrumentService
        .getAllInstruments()
        .filter { it.providerName == ProviderName.ALPHA_VANTAGE }

    instruments.forEach { instrument ->
      log.info("Retrieving data for instrument: ${instrument.symbol}")
      val dailyData = alphaVantageService.getDailyTimeSeriesForLastWeek(instrument.symbol)
      if (dailyData.isNotEmpty()) {
        dataProcessingUtil.processDailyData(instrument, dailyData, ProviderName.ALPHA_VANTAGE)
      } else {
        log.warn("No daily data found for instrument: ${instrument.symbol}")
      }
    }

    log.info("Completed AlphaVantage data retrieval execution. Processed ${instruments.size} instruments.")
  }
}
