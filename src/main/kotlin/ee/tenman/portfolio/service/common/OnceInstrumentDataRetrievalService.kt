package ee.tenman.portfolio.service.common

import ee.tenman.portfolio.job.BinanceDataRetrievalJob
import ee.tenman.portfolio.job.DailyPortfolioXirrJob
import ee.tenman.portfolio.job.FtDataRetrievalJob
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.summary.SummaryCacheService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
@Profile("!test")
@ConditionalOnProperty(name = ["scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class OnceInstrumentDataRetrievalService(
  private val ftDataRetrievalJob: FtDataRetrievalJob,
  private val binanceDataRetrievalJob: BinanceDataRetrievalJob,
  private val dailyPortfolioXirrJob: DailyPortfolioXirrJob,
  private val jobExecutionService: JobExecutionService,
  private val summaryCacheService: SummaryCacheService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct
  fun retrieveData() {
    CompletableFuture
      .runAsync {
        log.info("Retrieving data for all instruments")
        val allDailySummaries = summaryCacheService.getAllDailySummaries()

        if (allDailySummaries.isNotEmpty()) {
          log.info("Daily summaries found. Skipping instrument data retrieval job.")
          return@runAsync
        }

        log.info("No daily summaries found. Running instrument data retrieval job.")
        jobExecutionService.executeJob(ftDataRetrievalJob)
        jobExecutionService.executeJob(binanceDataRetrievalJob)
      }.thenRun {
        log.info("Running daily portfolio XIRR job")
        jobExecutionService.executeJob(dailyPortfolioXirrJob)
      }
  }
}
