package ee.tenman.portfolio.service.split

import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.InstrumentSplitRepository
import ee.tenman.portfolio.repository.JobExecutionRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Service
class SplitMetricsService(
  private val instrumentSplitRepository: InstrumentSplitRepository,
  private val instrumentRepository: InstrumentRepository,
  private val jobExecutionRepository: JobExecutionRepository,
  private val clock: Clock,
) : MeterBinder {
  private lateinit var announced: MultiGauge

  private lateinit var prices: MultiGauge

  @Volatile private var failed = 0.0

  override fun bindTo(registry: MeterRegistry) {
    announced = MultiGauge.builder("portfolio.split.announced").register(registry)
    prices = MultiGauge.builder("portfolio.instrument.price").register(registry)
    Gauge.builder("portfolio.split.check.failed", this) { it.failed }.register(registry)
  }

  fun refresh() {
    if (!::announced.isInitialized) return
    announced.register(
      instrumentSplitRepository.findPending(LocalDate.now(clock)).map {
        MultiGauge.Row.of(
          Tags.of("instrument", it.instrument.symbol, "ratio", it.ratio, "effective", it.effectiveDate.toString(), "source", it.sourceUrl),
          1,
        )
      },
      true,
    )
    prices.register(
      instrumentRepository
        .findAll()
        .filterNot { it.isCash() }
        .mapNotNull { instrument ->
          instrument.currentPrice?.takeIf { it > BigDecimal.ZERO }?.let { MultiGauge.Row.of(Tags.of("instrument", instrument.symbol), it) }
        },
      true,
    )
    failed = if (jobExecutionRepository.findFirstByJobNameOrderByStartTimeDesc(CHECK_JOB)?.status == JobStatus.FAILURE) 1.0 else 0.0
  }

  companion object {
    private const val CHECK_JOB = "SplitCheckJob"
  }
}
