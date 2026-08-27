package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEndWith
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toStartWith
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.TimeRange
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.cloud.openfeign.FeignClientProperties
import org.springframework.core.io.FileSystemResource

class TimeRangeConverterTest {
  private val converter = TimeRangeConverter()

  @Test
  fun `should convert a range code into its enum constant`() {
    expect(converter.convert("6M")).toEqual(TimeRange.SIX_MONTHS)
  }

  @Test
  fun `should throw when the range code is unknown`() {
    expect { converter.convert("42Y") }.toThrow<IllegalArgumentException>()
  }
}

class JsonMapperFactoryTest {
  @Test
  fun `should return the full json when the serialized value fits within the limit`() {
    expect(JsonMapperFactory.truncatedJson(mapOf("naam" to "Õäöü"))).toEqual("{\"naam\":\"Õäöü\"}")
  }

  @Test
  fun `should append an ellipsis marker when the serialized value exceeds the limit`() {
    expect(JsonMapperFactory.truncatedJson((1..1000).toList())).toEndWith(" ...")
  }

  @Test
  fun `should keep the head of the json when truncating an oversized value`() {
    expect(JsonMapperFactory.truncatedJson((1..1000).toList())).toStartWith("[1,2,3,")
  }
}

class Trading212ScrapingPropertiesTest {
  @Test
  fun `should return ticker for known symbol`() {
    val properties = Trading212ScrapingProperties()
    properties.symbols.add(Trading212SymbolEntry(symbol = "BNKE:PAR:EUR", ticker = "BNKEp_EQ"))

    val ticker = properties.findTickerBySymbol("BNKE:PAR:EUR")

    expect(ticker).toEqual("BNKEp_EQ")
  }

  @Test
  fun `should return null when symbol is unknown`() {
    val properties = Trading212ScrapingProperties()

    val ticker = properties.findTickerBySymbol("UNKNOWN:SYM:EUR")

    expect(ticker).toEqual(null)
  }
}

class FeignClientTimeoutPropertiesTest {
  @Test
  fun `should bind the openrouter read timeout at the prefix spring cloud openfeign declares`() {
    val prefix = FeignClientProperties::class.java.getAnnotation(ConfigurationProperties::class.java).value
    val yaml = YamlPropertySourceLoader().load("application.yml", FileSystemResource("src/main/resources/application.yml"))

    val bound = Binder(ConfigurationPropertySources.from(yaml)).bind(prefix, FeignClientProperties::class.java).get()

    expect(bound.config["openrouter"]?.readTimeout).toEqual(180000)
  }
}

class Resilience4jConfigurationTest {
  @Test
  fun `cannot retry job execution when job fails with illegal state`() {
    val predicate =
      Resilience4jConfiguration()
        .retryRegistry()
        .retry("job-execution")
        .retryConfig.exceptionPredicate

    expect(predicate.test(IllegalStateException("Sector classification failed for all 3 holdings"))).toEqual(false)
  }

  @Test
  fun `should retry job execution when job fails with generic exception`() {
    val predicate =
      Resilience4jConfiguration()
        .retryRegistry()
        .retry("job-execution")
        .retryConfig.exceptionPredicate

    expect(predicate.test(RuntimeException("connection reset by peer"))).toEqual(true)
  }
}

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
