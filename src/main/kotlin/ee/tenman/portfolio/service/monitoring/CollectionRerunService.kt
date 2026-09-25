package ee.tenman.portfolio.service.monitoring

import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.job.Job
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CollectionRerunService(
  private val jobs: List<Job>,
  private val jobExecutionService: JobExecutionService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun rerun(key: CollectionKey) {
    val name = CollectionMetricsService.JOBS.getValue(key)
    val job = jobs.find { it.getName() == name } ?: throw IllegalStateException("Job $name is not scheduled in this environment")
    log.info("Manual rerun requested for ${key.provider} ${key.operation}")
    Thread.ofVirtual().name("rerun-$name").start {
      runCatching { jobExecutionService.executeJob(job) }.onFailure { log.error("Manual rerun of $name failed", it) }
    }
  }
}
