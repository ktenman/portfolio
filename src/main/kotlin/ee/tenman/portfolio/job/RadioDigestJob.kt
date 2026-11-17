package ee.tenman.portfolio.job

import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import ee.tenman.portfolio.service.radio.DigestShow
import ee.tenman.portfolio.service.radio.RadioDigestService
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock

@ScheduledJob
class RadioDigestJob(
  radioDigestService: RadioDigestService,
  jobExecutionService: JobExecutionService,
  liveUpdateService: LiveUpdateService,
  clock: Clock,
) : RadioDigestShowJob(DigestShow.WEEK, radioDigestService, jobExecutionService, liveUpdateService, clock) {
  @Scheduled(cron = CollectionSchedules.RADIO_DIGEST_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runScheduledJob() = run()
}
