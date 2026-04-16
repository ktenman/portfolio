package ee.tenman.portfolio.openfigi

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class OpenFigiResolverTest {
  private val client = mockk<OpenFigiClient>()
  private val resolver = OpenFigiResolver(client, defaultExchangeCode = "IM")

  @Test
  fun `should resolve direct ticker match on first pass`() {
    every { client.map(listOf(OpenFigiQuery(idType = "TICKER", idValue = "UCG", exchCode = "IM"))) } returns
      listOf(
        OpenFigiEntry(
          data = listOf(OpenFigiMatch(figi = "X", name = "UNICREDIT SPA", ticker = "UCG", exchCode = "IM", securityType = "Common Stock")),
        ),
      )

    val name = resolver.resolveName("UCG")

    expect(name).toEqual("UNICREDIT SPA")
  }

  @Test
  fun `should retry without IT suffix when first pass returns warning`() {
    every { client.map(listOf(OpenFigiQuery("TICKER", "BAMIIT", "IM"))) } returns listOf(OpenFigiEntry(warning = "No identifier found."))
    every { client.map(listOf(OpenFigiQuery("TICKER", "BAMI", "IM"))) } returns
      listOf(
        OpenFigiEntry(
          data = listOf(OpenFigiMatch(figi = "Y", name = "BANCO BPM SPA", ticker = "BAMI", exchCode = "IM", securityType = "Common Stock")),
        ),
      )

    val name = resolver.resolveName("BAMIIT")

    expect(name).toEqual("BANCO BPM SPA")
    verify { client.map(listOf(OpenFigiQuery("TICKER", "BAMIIT", "IM"))) }
    verify { client.map(listOf(OpenFigiQuery("TICKER", "BAMI", "IM"))) }
  }

  @Test
  fun `cannot resolve when both passes fail`() {
    every { client.map(any()) } returns listOf(OpenFigiEntry(warning = "No identifier found."))

    val name = resolver.resolveName("ZZZ")

    expect(name).toEqual(null)
  }

  @Test
  fun `should return null and swallow upstream exception`() {
    every { client.map(any()) } throws RuntimeException("429")

    val name = resolver.resolveName("UCG")

    expect(name).toEqual(null)
  }
}
