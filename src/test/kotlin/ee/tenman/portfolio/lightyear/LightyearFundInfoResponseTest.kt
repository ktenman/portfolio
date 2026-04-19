package ee.tenman.portfolio.lightyear

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.math.BigDecimal

class LightyearFundInfoResponseTest {
  private val mapper =
    JsonMapper
      .builder()
      .addModule(KotlinModule.Builder().build())
      .build()

  @Test
  fun `maps lightyear json baseCurrency to fundCurrency kotlin field`() {
    val json =
      """
      {"ter": 0.12, "aum": 14836.44, "aumCurrency": "EUR", "baseCurrency": "USD", "shareCurrency": "EUR"}
      """.trimIndent()
    val response = mapper.readValue(json, LightyearFundInfoResponse::class.java)
    expect(response.fundCurrency).toEqual("USD")
    expect(response.ter).notToEqualNull().toEqualNumerically(BigDecimal("0.12"))
    expect(response.aumCurrency).toEqual("EUR")
  }

  @Test
  fun `fundCurrency is null when baseCurrency missing from response`() {
    val json = """{"ter": 0.2}"""
    val response = mapper.readValue(json, LightyearFundInfoResponse::class.java)
    expect(response.fundCurrency).toEqual(null)
  }
}
