package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.LiveUpdate
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import ee.tenman.portfolio.service.radio.DigestShow
import ee.tenman.portfolio.service.radio.RadioDigestService
import java.time.Clock
import java.time.LocalDate

abstract class RadioDigestShowJob(
  private val show: DigestShow,
  private val radioDigestService: RadioDigestService,
  private val jobExecutionService: JobExecutionService,
  private val liveUpdateService: LiveUpdateService,
  private val clock: Clock,
) : Job {
  protected fun run() {
    if (!radioDigestService.due(show, LocalDate.now(clock))) return
    runCatching { jobExecutionService.executeJob(this) }
      .onFailure { liveUpdateService.publish(LiveUpdate.DIGEST) }
      .getOrThrow()
  }

  override fun execute() {
    radioDigestService.build(show, LocalDate.now(clock))
  }

  override fun getName(): String = show.job
}
