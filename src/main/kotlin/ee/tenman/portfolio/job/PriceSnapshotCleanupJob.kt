package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.pricing.PriceSnapshotService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@ScheduledJob
class PriceSnapshotCleanupJob(
  private val jobExecutionService: JobExecutionService,
  private val priceSnapshotService: PriceSnapshotService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "\${scheduling.jobs.price-snapshot-cleanup-cron:0 0 3 * * *}")
  fun runJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    val cutoff = Instant.now(clock).minus(30, ChronoUnit.DAYS)
    log.info("Cleaning up price snapshots older than $cutoff")
    priceSnapshotService.deleteOlderThan(cutoff)
    log.info("Price snapshot cleanup completed")
  }
}
