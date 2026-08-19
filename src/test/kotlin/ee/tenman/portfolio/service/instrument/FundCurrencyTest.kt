package ee.tenman.portfolio.service.instrument

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.FundCurrencyOverridesProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.dto.EtfDetailDto
import ee.tenman.portfolio.dto.InstrumentDto
import ee.tenman.portfolio.lightyear.LightyearFundInfoData
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FundCurrencyTest {
  @Test
  fun `supports fundCurrency field`() {
    val dto =
      EtfDetailDto(
      instrumentId = 1,
      symbol = "VWCE",
      name = "Vanguard",
      allocation = BigDecimal.ZERO,
      ter = null,
      annualReturn = null,
      currentPrice = null,
      fundCurrency = Currency.USD,
    )
    expect(dto.fundCurrency).toEqual(Currency.USD)
  }

  @Test
  fun `fromEntity carries fundCurrency`() {
    val instrument =
      Instrument(
      symbol = "X:Y:Z",
        name = "N",
        category = "ETF",
        baseCurrency = "EUR",
    ).apply { fundCurrency = Currency.USD }

    val dto = InstrumentDto.fromEntity(instrument)

    expect(dto.fundCurrency).toEqual(Currency.USD)
  }

  @Test
  fun `fromEntity passes null fundCurrency through`() {
    val instrument = Instrument(symbol = "X", name = "N", category = "ETF", baseCurrency = "EUR")

    val dto = InstrumentDto.fromEntity(instrument)

    expect(dto.fundCurrency).toEqual(null)
  }
}

class FundCurrencyResolverServiceTest {
  private val llmLookup = mockk<FundCurrencyLlmLookupService>()

  private fun instrument(
    symbol: String = "BNKE:PAR:EUR",
    fund: Currency? = null,
  ) = Instrument(symbol = symbol, name = "Some ETF", category = "ETF", baseCurrency = "EUR").apply {
      fundCurrency = fund
    }

  private fun resolver(overrides: Map<String, String> = emptyMap()): FundCurrencyResolverService {
    val props = FundCurrencyOverridesProperties(overrides = overrides)
    props.validate()
    return FundCurrencyResolverService(props, llmLookup)
  }

  @Test
  fun `override wins over lightyear and llm`() {
    val r = resolver(overrides = mapOf("VWCE:GER:EUR" to "USD"))
    val result = r.resolve(instrument("VWCE:GER:EUR"), LightyearFundInfoData(null, Currency.EUR))
    expect(result).toEqual(Currency.USD)
    verify(exactly = 0) { llmLookup.lookup(any()) }
  }

  @Test
  fun `lightyear used when no override and code in allowlist`() {
    val r = resolver()
    val result = r.resolve(instrument("SPPW:GER:EUR"), LightyearFundInfoData(null, Currency.USD))
    expect(result).toEqual(Currency.USD)
    verify(exactly = 0) { llmLookup.lookup(any()) }
  }

  @Test
  fun `existing persisted fundCurrency short-circuits llm`() {
    val r = resolver()
    val result = r.resolve(instrument("X", fund = Currency.EUR), LightyearFundInfoData(null, null))
    expect(result).toEqual(Currency.EUR)
    verify(exactly = 0) { llmLookup.lookup(any()) }
  }

  @Test
  fun `llm called when no override, no lightyear, no existing value`() {
    val r = resolver()
    every { llmLookup.lookup(any()) } returns Currency.EUR
    val result = r.resolve(instrument("X"), null)
    expect(result).toEqual(Currency.EUR)
    verify(exactly = 1) { llmLookup.lookup(any()) }
  }

  @Test
  fun `returns null when all tiers fail`() {
    val r = resolver()
    every { llmLookup.lookup(any()) } returns null
    val result = r.resolve(instrument("X"), null)
    expect(result).toEqual(null)
  }
}

class FundCurrencyLlmLookupServiceTest {
  private val openRouterClient = mockk<OpenRouterClient>()
  private val service = FundCurrencyLlmLookupService(openRouterClient)

  private fun instrument(
    symbol: String = "BNKE:PAR:EUR",
    name: String = "Amundi Euro Stoxx Banks",
  ) = Instrument(symbol = symbol, name = name, category = "ETF", baseCurrency = "EUR")

  @Test
  fun `returns valid allowlist currency from clean json response`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_6_LUNA, any()) } returns "{\"currency\":\"EUR\"}"

    val result = service.lookup(instrument())

    expect(result).toEqual(Currency.EUR)
  }

  @Test
  fun `returns null when llm returns explicit null`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_6_LUNA, any()) } returns "{\"currency\":null}"

    expect(service.lookup(instrument())).toEqual(null)
  }

  @Test
  fun `returns null for non-allowlist currency`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_6_LUNA, any()) } returns "{\"currency\":\"XYZ\"}"

    expect(service.lookup(instrument())).toEqual(null)
  }

  @Test
  fun `returns null when json is malformed`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_6_LUNA, any()) } returns "not json at all"

    expect(service.lookup(instrument())).toEqual(null)
  }

  @Test
  fun `returns null when llm call returns null`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_6_LUNA, any()) } returns null

    expect(service.lookup(instrument())).toEqual(null)
  }

  @Test
  fun `normalizes lowercase code to uppercase`() {
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_6_LUNA, any()) } returns "{\"currency\":\"usd\"}"

    expect(service.lookup(instrument())).toEqual(Currency.USD)
  }

  @Test
  fun `prompt includes symbol and name`() {
    val captured = slot<String>()
    every { openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_6_LUNA, capture(captured)) } returns "{\"currency\":\"EUR\"}"

    service.lookup(instrument(symbol = "VWCE:GER:EUR", name = "Vanguard FTSE All-World"))

    expect(captured.captured).toContain("VWCE:GER:EUR")
    expect(captured.captured).toContain("Vanguard FTSE All-World")
  }
}
