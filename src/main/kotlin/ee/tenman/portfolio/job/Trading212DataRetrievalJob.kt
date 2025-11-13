package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.JobExecutionService
import ee.tenman.portfolio.trading212.Trading212Service
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
  name = ["scheduling.enabled", "scheduling.jobs.trading212-enabled"],
  havingValue = "true",
  matchIfMissing = false,
)
class Trading212DataRetrievalJob(
  private val jobExecutionService: JobExecutionService,
  private val trading212Service: Trading212Service,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${scheduling.jobs.trading212-interval:60000}")
  fun runJob() {
    log.info("Running Trading212 price update job")
    jobExecutionService.executeJob(this)
    log.info("Completed Trading212 price update job")
  }

  override fun execute() {
    log.info("Starting Trading212 price update execution")
    try {
      trading212Service.updateCurrentPrices()
    } catch (e: Exception) {
      log.error("Failed to update Trading212 prices", e)
      throw e
    }
  }
}
