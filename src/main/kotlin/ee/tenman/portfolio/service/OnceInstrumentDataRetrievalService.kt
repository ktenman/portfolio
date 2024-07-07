package ee.tenman.portfolio.service

import ee.tenman.portfolio.job.DailyPortfolioXirrJob
import ee.tenman.portfolio.job.InstrumentDataRetrievalJob
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("!test")
class OnceInstrumentDataRetrievalService(
  private val instrumentDataRetrievalJob: InstrumentDataRetrievalJob,
  private val dailyPortfolioXirrJob: DailyPortfolioXirrJob,
  private val jobExecutionService: JobExecutionService,
  private val portfolioSummaryService: PortfolioSummaryService
) {
  companion object {
    private val log = LoggerFactory.getLogger(OnceInstrumentDataRetrievalService::class.java)
  }

  @PostConstruct
  fun retrieveData() {
    log.info("Retrieving data for all instruments")
    val allDailySummaries = portfolioSummaryService.getAllDailySummaries()
    if (allDailySummaries.isNotEmpty()) {
      log.info("Data already retrieved. Skipping.")
      return
    }
    log.info("No daily summaries found. Running instrument data retrieval job.")
    jobExecutionService.executeJob(instrumentDataRetrievalJob)
    jobExecutionService.executeJob(dailyPortfolioXirrJob)
  }
}
