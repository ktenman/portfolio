package ee.tenman.portfolio.trading212

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.configuration.Trading212SymbolEntry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class Trading212ServiceTest {
  private val client = mockk<Trading212Client>()
  private val properties =
    Trading212ScrapingProperties().apply {
      symbols.addAll(
        listOf(
          Trading212SymbolEntry(symbol = "BNKE:PAR:EUR", ticker = "BNKEp_EQ"),
          Trading212SymbolEntry(symbol = "VUAA:GER:EUR", ticker = "VUAAm_EQ"),
        ),
      )
    }
  private val service = Trading212Service(client, properties)

  @Test
  fun `should map prices from config-driven ticker list`() {
    val response =
      Trading212Response(
        data =
          mapOf(
            "BNKEp_EQ" to Trading212PriceData(bid = BigDecimal("330.77"), spread = BigDecimal("0.05"), timestamp = "2026-04-16T10:00:00Z"),
            "VUAAm_EQ" to Trading212PriceData(bid = BigDecimal("100.50"), spread = BigDecimal("0.02"), timestamp = "2026-04-16T10:00:00Z"),
          ),
      )
    every { client.getPrices("BNKEp_EQ,VUAAm_EQ") } returns response
    val prices = service.fetchCurrentPrices()
    expect(prices.size).toEqual(2)
    expect(prices["BNKE:PAR:EUR"]!!).toEqualNumerically(BigDecimal("330.77"))
    expect(prices["VUAA:GER:EUR"]!!).toEqualNumerically(BigDecimal("100.50"))
    verify { client.getPrices("BNKEp_EQ,VUAAm_EQ") }
  }

  @Test
  fun `should return empty map when properties have no symbols`() {
    val emptyProperties = Trading212ScrapingProperties()
    val emptyService = Trading212Service(client, emptyProperties)
    val prices = emptyService.fetchCurrentPrices()
    expect(prices.size).toEqual(0)
  }

  @Test
  fun `cannot return price when ticker missing from upstream response`() {
    every { client.getPrices(any()) } returns Trading212Response(data = emptyMap())
    val prices = service.fetchCurrentPrices()
    expect(prices.size).toEqual(0)
  }
}
