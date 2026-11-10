package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.OpenRouterCall
import ee.tenman.portfolio.repository.OpenRouterCallRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@IntegrationTest
class OpenRouterSpendServiceIT {
  @Resource private lateinit var repository: OpenRouterCallRepository

  private val clock = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneId.of("Europe/Tallinn"))

  private val argon = "google/gemini-4-argon"

  private fun bound(registry: SimpleMeterRegistry): OpenRouterSpendService =
    OpenRouterSpendService(repository, clock).also { it.bindTo(registry) }

  private fun refreshed(): SimpleMeterRegistry = SimpleMeterRegistry().also { bound(it).refresh() }

  private fun spend(
    registry: SimpleMeterRegistry,
    month: String,
    purpose: String = "sector",
  ): Double =
    registry
      .get("portfolio.openrouter.month.cost.dollars")
      .tags("month", month, "purpose", purpose, "model", argon)
      .gauge()
      .value()

  private fun months(registry: SimpleMeterRegistry): List<String?> =
    registry.find("portfolio.openrouter.month.cost.dollars").gauges().map { it.id.getTag("month") }

  @Test
  fun `publishes the spend of each month by purpose and model`() {
    repository.saveAll(
      listOf(
        OpenRouterCall(LocalDate.of(2026, 9, 3), "sector", argon, BigDecimal("0.5")),
        OpenRouterCall(LocalDate.of(2026, 9, 30), "sector", argon, BigDecimal("0.00000012")),
        OpenRouterCall(LocalDate.of(2026, 10, 2), "sector", argon, BigDecimal("7")),
      ),
    )
    expect(spend(refreshed(), "2026-09")).toEqual(0.50000012)
  }

  @Test
  fun `publishes the spend of a purpose the code has dropped`() {
    repository.save(OpenRouterCall(LocalDate.of(2026, 11, 2), "podcast_summary", argon, BigDecimal("4")))
    expect(spend(refreshed(), "2026-11", "podcast_summary")).toEqual(4.0)
  }

  @Test
  fun `leaves out months before the twelve preceding the current one`() {
    repository.saveAll(
      listOf(
        OpenRouterCall(LocalDate.of(2025, 10, 31), "radio_outline", argon, BigDecimal("1")),
        OpenRouterCall(LocalDate.of(2025, 11, 1), "radio_outline", argon, BigDecimal("2")),
      ),
    )
    expect(months(refreshed())).toContainExactly("2025-11")
  }

  @Test
  fun `updates the spend of a month on the next refresh`() {
    val registry = SimpleMeterRegistry()
    val service = bound(registry)
    repository.save(OpenRouterCall(LocalDate.of(2026, 11, 2), "sector", argon, BigDecimal("1")))
    service.refresh()
    repository.save(OpenRouterCall(LocalDate.of(2026, 11, 5), "sector", argon, BigDecimal("2")))
    service.refresh()
    expect(spend(registry, "2026-11")).toEqual(3.0)
  }

  @Test
  fun `drops a month once it has no calls left`() {
    val registry = SimpleMeterRegistry()
    val service = bound(registry)
    repository.save(OpenRouterCall(LocalDate.of(2026, 11, 2), "license_plate", argon, BigDecimal("1")))
    service.refresh()
    repository.deleteAll()
    service.refresh()
    expect(months(registry)).toBeEmpty()
  }
}
