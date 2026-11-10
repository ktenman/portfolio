package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiPurpose
import ee.tenman.portfolio.domain.OpenRouterCall
import ee.tenman.portfolio.repository.OpenRouterCallRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

@Service
class OpenRouterSpendService(
  private val repository: OpenRouterCallRepository,
  private val clock: Clock,
) : MeterBinder {
  private val log = LoggerFactory.getLogger(javaClass)

  private lateinit var months: MultiGauge

  override fun bindTo(registry: MeterRegistry) {
    months = MultiGauge.builder("portfolio.openrouter.month.cost.dollars").register(registry)
  }

  fun record(
    purpose: AiPurpose,
    model: String,
    cost: Double,
  ) {
    runCatching { repository.save(OpenRouterCall(LocalDate.now(clock), purpose.name.lowercase(), model, BigDecimal.valueOf(cost))) }
      .onFailure { log.warn("Failed to save the OpenRouter cost of $purpose on $model: ${it.message}") }
  }

  fun refresh() {
    if (!::months.isInitialized) return
    months.register(
      repository.findMonthlySpendSince(YearMonth.now(clock).minusMonths(12).atDay(1)).map {
        MultiGauge.Row.of(Tags.of("month", it.month, "purpose", it.purpose, "model", it.model), it.cost)
      },
      true,
    )
  }
}
