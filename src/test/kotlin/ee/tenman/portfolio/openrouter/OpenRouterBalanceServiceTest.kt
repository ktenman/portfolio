package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import feign.FeignException
import feign.Request
import feign.Response
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class OpenRouterBalanceServiceTest {
  private val client = mockk<OpenRouterAccountClient>()
  private var now = Instant.parse("2026-11-10T17:00:00Z")

  private val clock =
    object : Clock() {
      override fun getZone(): ZoneId = ZoneOffset.UTC

      override fun withZone(zone: ZoneId): Clock = this

      override fun instant(): Instant = now
    }

  private fun refreshed(managementKey: String = "mgmt-ключ"): SimpleMeterRegistry {
    val registry = SimpleMeterRegistry()
    OpenRouterBalanceService(client, OpenRouterProperties(apiKey = "api-võti", managementKey = managementKey), clock)
      .also { it.bindTo(registry) }
      .refresh()
    return registry
  }

  private fun SimpleMeterRegistry.gauge(name: String): Double = get(name).gauge().value()

  private fun account(
    credits: Double = 25.0,
    usage: Double = 21.5,
    keyUsage: Double = 7.25,
  ) {
    every { client.credits(any()) } returns OpenRouterCredits(OpenRouterCredits.Data(credits, usage))
    every { client.key(any()) } returns OpenRouterKey(OpenRouterKey.Data(keyUsage))
  }

  private fun unauthorized(): FeignException {
    val request = Request.create(Request.HttpMethod.GET, "https://openrouter.ai/api/v1/credits", emptyMap(), null, null, null)
    val response =
      Response
        .builder()
        .status(401)
        .reason("Unauthorized")
        .request(request)
        .headers(emptyMap())
        .body("""{"error":{"message":"No auth credentials found"}}""", Charsets.UTF_8)
        .build()
    return FeignException.errorStatus("credits", response)
  }

  @Test
  fun `publishes the remaining balance as credits minus usage`() {
    account()
    expect(refreshed().gauge("portfolio.openrouter.balance.dollars")).toEqual(3.5)
  }

  @Test
  fun `publishes the account usage`() {
    account()
    expect(refreshed().gauge("portfolio.openrouter.account.usage.dollars")).toEqual(21.5)
  }

  @Test
  fun `publishes this key's usage`() {
    account()
    expect(refreshed().gauge("portfolio.openrouter.key.usage.dollars")).toEqual(7.25)
  }

  @Test
  fun `publishes when the balance was last read`() {
    account()
    expect(refreshed().gauge("portfolio.openrouter.balance.read.timestamp.seconds")).toEqual(1794330000.0)
  }

  @Test
  fun `reads the credits with the management key and the key usage with the api key`() {
    account()
    refreshed()
    verify {
      client.credits("Bearer mgmt-ключ")
      client.key("Bearer api-võti")
    }
  }

  @Test
  fun `publishes no balance without a management key`() {
    account()
    val registry = refreshed(managementKey = "")
    val gauges =
      listOf(
        "portfolio.openrouter.balance.dollars",
        "portfolio.openrouter.balance.read.timestamp.seconds",
        "portfolio.openrouter.key.usage.dollars",
      ).map { registry.gauge(it) }
    verify(exactly = 0) { client.credits(any()) }
    expect(gauges).toEqual(listOf(Double.NaN, 1794330000.0, 7.25))
  }

  @Test
  fun `counts the last read from the first poll until the credits are read`() {
    every { client.credits(any()) } throws unauthorized()
    every { client.key(any()) } returns OpenRouterKey(OpenRouterKey.Data(7.25))
    expect(refreshed().gauge("portfolio.openrouter.balance.read.timestamp.seconds")).toEqual(1794330000.0)
  }

  @Test
  fun `keeps the last balance when the credits cannot be read`() {
    val registry = SimpleMeterRegistry()
    val service = OpenRouterBalanceService(client, OpenRouterProperties(apiKey = "api-võti", managementKey = "mgmt-ключ"), clock)
    service.bindTo(registry)
    account()
    service.refresh()
    now = now.plusSeconds(300)
    every { client.credits(any()) } throws unauthorized()
    every { client.key(any()) } returns OpenRouterKey(OpenRouterKey.Data(8.0))
    service.refresh()
    val gauges =
      listOf(
        "portfolio.openrouter.balance.dollars",
        "portfolio.openrouter.balance.read.timestamp.seconds",
        "portfolio.openrouter.key.usage.dollars",
      ).map { registry.gauge(it) }
    expect(gauges).toEqual(listOf(3.5, 1794330000.0, 8.0))
  }
}
