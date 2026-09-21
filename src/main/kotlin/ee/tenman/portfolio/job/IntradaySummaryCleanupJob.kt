package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.pricing.InstrumentMinutePriceService
import ee.tenman.portfolio.service.summary.IntradaySummaryService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val MIN_INTRADAY_RETENTION_DAYS = 9L

@ScheduledJob
class IntradaySummaryCleanupJob(
  private val jobExecutionService: JobExecutionService,
  private val intradaySummaryService: IntradaySummaryService,
  private val instrumentMinutePriceService: InstrumentMinutePriceService,
  private val clock: Clock,
  @Value("\${scheduling.jobs.intraday-summary-retention-days:30}")
  private val retentionDays: Long,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "\${scheduling.jobs.intraday-summary-cleanup-cron:0 30 4 * * *}")
  fun runJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    val cutoff = Instant.now(clock).minus(retentionDays.coerceAtLeast(MIN_INTRADAY_RETENTION_DAYS), ChronoUnit.DAYS)
    log.info("Cleaning up intraday summaries older than $cutoff")
    intradaySummaryService.deleteOlderThan(cutoff)
    instrumentMinutePriceService.deleteOlderThan(cutoff)
    log.info("Intraday summary cleanup completed")
  }
}
