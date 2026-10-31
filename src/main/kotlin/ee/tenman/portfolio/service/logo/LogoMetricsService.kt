package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.job.EtfLogoCollectionJob
import ee.tenman.portfolio.repository.JobExecutionRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Service

@Service
class LogoMetricsService(
  private val jobExecutionRepository: JobExecutionRepository,
) : MeterBinder {
  override fun bindTo(registry: MeterRegistry) {
    Gauge.builder("portfolio.logo.collection.failed", this) { it.failed() }.register(registry)
  }

  private fun failed(): Double =
    if (jobExecutionRepository.findFirstByJobNameOrderByStartTimeDesc(COLLECTION_JOB)?.status == JobStatus.FAILURE) 1.0 else 0.0

  companion object {
    private val COLLECTION_JOB = EtfLogoCollectionJob::class.java.simpleName
  }
}
