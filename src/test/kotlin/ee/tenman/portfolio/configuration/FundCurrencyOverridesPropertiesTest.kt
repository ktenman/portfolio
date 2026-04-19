package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Currency
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FundCurrencyOverridesPropertiesTest {
  @Test
  fun `returns override for exact symbol match`() {
    val props = FundCurrencyOverridesProperties(overrides = mapOf("VWCE:GER:EUR" to "USD"))
    props.validate()
    expect(props.forSymbol("VWCE:GER:EUR")).toEqual(Currency.USD)
  }

  @Test
  fun `returns null for unknown symbol`() {
    val props = FundCurrencyOverridesProperties(overrides = mapOf("FOO:BAR:EUR" to "EUR"))
    props.validate()
    expect(props.forSymbol("UNKNOWN:XYZ:USD")).toEqual(null)
  }

  @Test
  fun `normalizes currency code to uppercase`() {
    val props = FundCurrencyOverridesProperties(overrides = mapOf("FOO:BAR:EUR" to "usd"))
    props.validate()
    expect(props.forSymbol("FOO:BAR:EUR")).toEqual(Currency.USD)
  }

  @Test
  fun `validate fails fast on non-allowlist currency code`() {
    val props = FundCurrencyOverridesProperties(overrides = mapOf("FOO:BAR:EUR" to "XYZ"))
    val ex = assertThrows<IllegalStateException> { props.validate() }
    expect(ex.message!!.contains("XYZ")).toEqual(true)
    expect(ex.message!!.contains("FOO:BAR:EUR")).toEqual(true)
  }

  @Test
  fun `empty overrides is valid`() {
    val props = FundCurrencyOverridesProperties()
    props.validate()
    expect(props.forSymbol("ANY")).toEqual(null)
  }
}
