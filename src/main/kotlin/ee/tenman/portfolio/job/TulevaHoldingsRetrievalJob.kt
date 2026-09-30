package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.etf.HoldingImportService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import ee.tenman.portfolio.tuleva.TulevaHoldingsService
import ee.tenman.portfolio.tuleva.TulevaHoldingsService.Companion.SYMBOL
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.LocalDate

@ScheduledJob
class TulevaHoldingsRetrievalJob(
  private val tulevaHoldingsService: TulevaHoldingsService,
  private val holdingImportService: HoldingImportService,
  private val jobExecutionService: JobExecutionService,
  private val clock: Clock,
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  @Scheduled(initialDelay = CollectionSchedules.TULEVA_HOLDINGS_STARTUP_SECONDS * 1000, fixedDelay = Long.MAX_VALUE)
  fun runStartupImport() {
    jobExecutionService.executeJob(this)
  }

  @Scheduled(cron = CollectionSchedules.TULEVA_HOLDINGS_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runScheduledJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    collectionMonitor.collect(CollectionKey.TULEVA_HOLDINGS, listOf(SYMBOL)) { run ->
      run.attempted(SYMBOL)
      refresh(run)
    }
  }

  private fun refresh(run: CollectionRun) {
    val imported = runCatching { tulevaHoldingsService.importReports() }
    val holdings =
      runCatching { tulevaHoldingsService.lookThrough() }.getOrElse { failure ->
        throw imported.exceptionOrNull()?.also { if (it !== failure) it.addSuppressed(failure) } ?: failure
      }
    run.fetched(SYMBOL)
    holdingImportService.queue(SYMBOL, LocalDate.now(clock), holdings)
    imported.getOrThrow()
    run.persisted(SYMBOL)
  }
}
