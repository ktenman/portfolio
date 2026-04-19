package ee.tenman.portfolio.service.instrument

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test

class FundCurrencyLlmLookupServiceTest {
  private val openRouterClient = mockk<OpenRouterClient>()
  private val service = FundCurrencyLlmLookupService(openRouterClient)

  private fun instrument(
    symbol: String = "BNKE:PAR:EUR",
    name: String = "Amundi Euro Stoxx Banks",
  ) = Instrument(symbol = symbol, name = name, category = "ETF", baseCurrency = "EUR")

  @Test
  fun `returns valid allowlist currency from clean json response`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, any()) } returns "{\"currency\":\"EUR\"}"

    val result = service.lookup(instrument())

    expect(result).toEqual(Currency.EUR)
  }

  @Test
  fun `returns null when llm returns explicit null`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, any()) } returns "{\"currency\":null}"

    expect(service.lookup(instrument())).toEqual(null)
  }

  @Test
  fun `returns null for non-allowlist currency`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, any()) } returns "{\"currency\":\"XYZ\"}"

    expect(service.lookup(instrument())).toEqual(null)
  }

  @Test
  fun `returns null when json is malformed`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, any()) } returns "not json at all"

    expect(service.lookup(instrument())).toEqual(null)
  }

  @Test
  fun `returns null when llm call returns null`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, any()) } returns null

    expect(service.lookup(instrument())).toEqual(null)
  }

  @Test
  fun `normalizes lowercase code to uppercase`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, any()) } returns "{\"currency\":\"usd\"}"

    expect(service.lookup(instrument())).toEqual(Currency.USD)
  }

  @Test
  fun `prompt includes symbol and name`() {
    val captured = slot<String>()
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, capture(captured)) } returns "{\"currency\":\"EUR\"}"

    service.lookup(instrument(symbol = "VWCE:GER:EUR", name = "Vanguard FTSE All-World"))

    expect(captured.captured).toContain("VWCE:GER:EUR")
    expect(captured.captured).toContain("Vanguard FTSE All-World")
  }
}
