package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioEpisodeService
import ee.tenman.portfolio.service.radio.RadioProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.nio.file.Files

@ScheduledJob
class RadioEpisodeJob(
  private val radioEpisodeService: RadioEpisodeService,
  private val radioProperties: RadioProperties,
  private val jobExecutionService: JobExecutionService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(cron = "\${scheduling.jobs.radio-episode-cron:0 */10 * * * *}")
  fun runScheduledJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    val recordings = radioProperties.recordings
    if (!Files.isDirectory(recordings)) return
    Files.list(recordings).use { it.toList() }.filter { Files.isDirectory(it) }.sorted().forEach { directory ->
      runCatching { radioEpisodeService.make(directory) }.onFailure { log.error("Radio episode job failed for $directory", it) }
    }
  }
}
