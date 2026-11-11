package ee.tenman.portfolio.job

import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.poff.ImdbMatchService
import ee.tenman.portfolio.service.poff.ImdbRatingService
import ee.tenman.portfolio.service.poff.PoffCatalogueService
import ee.tenman.portfolio.service.poff.PoffImportService
import ee.tenman.portfolio.service.poff.PoffListing
import ee.tenman.portfolio.service.poff.PoffStillService
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@ScheduledJob
class PoffImportJob(
  private val poffImportService: PoffImportService,
  private val poffCatalogueService: PoffCatalogueService,
  private val imdbMatchService: ImdbMatchService,
  private val imdbRatingService: ImdbRatingService,
  private val poffStillService: PoffStillService,
  private val jobExecutionService: JobExecutionService,
  private val taskScheduler: TaskScheduler,
  private val clock: Clock,
) : Job,
  MeterBinder {
  private val attempted = AtomicReference<Instant>()
  private val running = AtomicBoolean()
  private val unrated = AtomicBoolean()
  private val failed = AtomicBoolean()

  override fun bindTo(registry: MeterRegistry) {
    Gauge.builder("portfolio.poff.import.failed", failed) { if (it.get()) 1.0 else 0.0 }.register(registry)
  }

  @PostConstruct
  fun scheduleInitialRun() {
    taskScheduler.schedule({ runScheduledJob() }, clock.instant().plusSeconds(5))
  }

  @Scheduled(cron = "\${scheduling.jobs.poff-import-cron:0 0/30 8-23 * * *}", zone = CollectionSchedules.TIME_ZONE)
  fun runScheduledJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    if (!running.compareAndSet(false, true)) return
    runCatching(::sync).also { running.set(false) }.getOrElse {
      failed.set(true)
      throw IllegalStateException("PÖFF import failed: ${it.message}", it)
    }
  }

  private fun sync() {
    val due = poffCatalogueService.due(attempted.get())
    if (due) crawl()
    val pending = unrated.get()
    if (pending) rate()
    if (due || pending) failed.set(false)
  }

  private fun crawl() {
    attempted.set(clock.instant())
    val listing = poffImportService.listing()
    if (!poffCatalogueService.settled(listing)) update(listing)
  }

  private fun rate() {
    imdbRatingService.refresh()
    unrated.set(false)
  }

  private fun update(listing: List<PoffListing>) {
    val catalogue = poffImportService.fetch(listing)
    poffCatalogueService.store(catalogue)
    if (imdbMatchService.match(catalogue.films) > 0) unrated.set(true)
    poffStillService.store(catalogue.films)
  }
}
