package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.pricing.PriceSnapshotService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@ScheduledJob
class PriceSnapshotCleanupJob(
  private val jobExecutionService: JobExecutionService,
  private val priceSnapshotService: PriceSnapshotService,
  private val clock: Clock,
  @Value("\${scheduling.jobs.price-snapshot-retention-days:30}")
  private val retentionDays: Long,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "\${scheduling.jobs.price-snapshot-cleanup-cron:0 0 4 * * *}")
  fun runJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    val cutoff = Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS)
    log.info("Cleaning up price snapshots older than $cutoff")
    priceSnapshotService.deleteOlderThan(cutoff)
    log.info("Price snapshot cleanup completed")
  }
}
