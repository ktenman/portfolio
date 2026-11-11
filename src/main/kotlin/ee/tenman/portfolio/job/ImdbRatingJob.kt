package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import ee.tenman.portfolio.service.poff.ImdbRatingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class ImdbRatingJob(
  private val imdbRatingService: ImdbRatingService,
  private val jobExecutionService: JobExecutionService,
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = CollectionSchedules.IMDB_RATINGS_STARTUP_SECONDS * 1000, fixedDelay = Long.MAX_VALUE)
  fun runStartupImport() {
    if (collectionMonitor.current(CollectionKey.IMDB_RATINGS, listOf(DATASET))) {
      log.info("Skipping startup IMDb ratings download because the last one is still current")
      return
    }
    jobExecutionService.executeJob(this)
  }

  @Scheduled(cron = CollectionSchedules.IMDB_RATINGS_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runScheduledJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    collectionMonitor.collect(CollectionKey.IMDB_RATINGS, listOf(DATASET)) { run ->
      run.attempted(DATASET)
      imdbRatingService.refresh()
      run.persisted(DATASET, changed = false)
    }
  }

  companion object {
    const val DATASET = "title.ratings.tsv.gz"
  }
}
