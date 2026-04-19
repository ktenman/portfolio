package ee.tenman.portfolio.service.instrument

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.FundCurrencyOverridesProperties
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.lightyear.LightyearFundInfoData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

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
