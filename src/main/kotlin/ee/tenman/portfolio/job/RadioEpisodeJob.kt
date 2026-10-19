package ee.tenman.portfolio.job

import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.RadioEpisodeService
import ee.tenman.portfolio.service.radio.RadioProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.nio.file.Files
import java.nio.file.Path

@ScheduledJob
class RadioEpisodeJob(
  private val radioEpisodeService: RadioEpisodeService,
  private val radioProperties: RadioProperties,
  private val jobExecutionService: JobExecutionService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${scheduling.jobs.radio-episode-delay:15000}")
  fun runScheduledJob() {
    if (directories().none(::pending)) return
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    directories().forEach { directory ->
      runCatching { radioEpisodeService.make(directory) }.onFailure { log.error("Radio episode job failed for $directory", it) }
    }
  }

  private fun pending(directory: Path): Boolean =
    runCatching { radioEpisodeService.todo(directory).isNotEmpty() }
      .onFailure { log.warn("Cannot tell whether $directory has cuts left to find", it) }
      .getOrDefault(false)

  private fun directories(): List<Path> {
    val recordings = radioProperties.recordings
    if (!Files.isDirectory(recordings)) return emptyList()
    return Files
      .list(recordings)
      .use { it.toList() }
      .filter { Files.isDirectory(it) }
      .sorted()
  }
}
