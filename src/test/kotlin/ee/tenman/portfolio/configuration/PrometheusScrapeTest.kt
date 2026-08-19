package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.verbs.expect
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Test

class PrometheusScrapeTest {
  @Test
  fun `should scrape metrics as text when the protobuf exposition format is absent`() {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    registry.counter("portfolio_scrape_check", "status", "õnnestus").increment()
    expect(registry.scrape()).toContain("portfolio_scrape_check", "õnnestus")
  }
}
