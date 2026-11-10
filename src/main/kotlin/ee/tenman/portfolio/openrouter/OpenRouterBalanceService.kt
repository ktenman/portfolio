package ee.tenman.portfolio.openrouter

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class OpenRouterBalanceService(
  private val client: OpenRouterAccountClient,
  private val properties: OpenRouterProperties,
  private val clock: Clock,
) : MeterBinder {
  private val log = LoggerFactory.getLogger(javaClass)

  @Volatile private var balance = Double.NaN

  @Volatile private var accountUsage = Double.NaN

  @Volatile private var keyUsage = Double.NaN

  @Volatile private var read = 0.0

  override fun bindTo(registry: MeterRegistry) {
    Gauge.builder("portfolio.openrouter.balance.dollars", this) { it.balance }.register(registry)
    Gauge.builder("portfolio.openrouter.account.usage.dollars", this) { it.accountUsage }.register(registry)
    Gauge.builder("portfolio.openrouter.key.usage.dollars", this) { it.keyUsage }.register(registry)
    Gauge.builder("portfolio.openrouter.balance.read.timestamp.seconds", this) { it.read }.register(registry)
  }

  fun refresh() {
    if (read == 0.0) read = now()
    readCredits()
    readKey()
  }

  private fun readCredits() {
    if (properties.managementKey.isBlank()) return
    runCatching { client.credits("Bearer ${properties.managementKey}").data }
      .onSuccess {
        balance = it.totalCredits - it.totalUsage
        accountUsage = it.totalUsage
        read = now()
      }.onFailure { log.warn("Failed to read the OpenRouter credits: ${it.message}") }
  }

  private fun readKey() {
    if (properties.apiKey.isBlank()) return
    runCatching { client.key("Bearer ${properties.apiKey}").data.usage }
      .onSuccess { keyUsage = it }
      .onFailure { log.warn("Failed to read the OpenRouter key usage: ${it.message}") }
  }

  private fun now(): Double = clock.instant().epochSecond.toDouble()
}
