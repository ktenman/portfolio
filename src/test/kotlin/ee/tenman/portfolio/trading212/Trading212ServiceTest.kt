package ee.tenman.portfolio.trading212

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.configuration.Trading212SymbolEntry
import ee.tenman.portfolio.dto.HoldingData
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
  fun `should map prices only for eligible Trading212-provider symbols`() {
    val response =
      Trading212Response(
        data =
          mapOf(
            "BNKEp_EQ" to Trading212PriceData(bid = BigDecimal("330.77"), spread = BigDecimal("0.05"), timestamp = "2026-04-16T10:00:00Z"),
          ),
      )
    every { client.getPrices("BNKEp_EQ") } returns response

    val prices = service.fetchCurrentPrices(setOf("BNKE:PAR:EUR"))

    expect(prices.size).toEqual(1)
    expect(prices["BNKE:PAR:EUR"]).notToEqualNull().toEqualNumerically(BigDecimal("330.77"))
    verify { client.getPrices("BNKEp_EQ") }
  }

  @Test
  fun `cannot call upstream when eligible symbol set is empty`() {
    val prices = service.fetchCurrentPrices(emptySet())

    expect(prices.size).toEqual(0)
    verify(exactly = 0) { client.getPrices(any()) }
  }

  @Test
  fun `cannot call upstream when none of the eligible symbols are configured`() {
    val prices = service.fetchCurrentPrices(setOf("UNKNOWN:PAR:EUR"))

    expect(prices.size).toEqual(0)
    verify(exactly = 0) { client.getPrices(any()) }
  }

  @Test
  fun `cannot return price when ticker missing from upstream response`() {
    every { client.getPrices(any()) } returns Trading212Response(data = emptyMap())

    val prices = service.fetchCurrentPrices(setOf("BNKE:PAR:EUR", "VUAA:GER:EUR"))

    expect(prices.size).toEqual(0)
  }
}

class Trading212CatalogueServiceTest {
  private val client = mockk<Trading212CatalogueClient>()
  private val service = Trading212CatalogueService(client)

  @Test
  fun `should return instrument by exact ticker match`() {
    val instrument =
      Trading212Instrument(
        ticker = "SANe_EQ",
        type = "STOCK",
        isin = "ES0113900J37",
        currencyCode = "EUR",
        name = "Banco Santander",
        shortName = "SAN",
      )
    every { client.fetchInstruments() } returns listOf(instrument)
    val result = service.getInstrumentByTicker("SANe_EQ")
    expect(result).notToEqualNull()
    expect(result!!.name).toEqual("Banco Santander")
  }

  @Test
  fun `should return null when ticker not in catalogue`() {
    every { client.fetchInstruments() } returns emptyList()
    val result = service.getInstrumentByTicker("UNKNOWN_EQ")
    expect(result).toEqual(null)
  }

  @Test
  fun `should fetch catalogue once and serve from in-memory cache`() {
    every { client.fetchInstruments() } returns
      listOf(
        Trading212Instrument("SANe_EQ", "STOCK", "ES0113900J37", "EUR", "Banco Santander", "SAN"),
      )
    service.getInstrumentByTicker("SANe_EQ")
    service.getInstrumentByTicker("SANe_EQ")
    verify(exactly = 1) { client.fetchInstruments() }
  }

  @Test
  fun `cannot fail when upstream throws and returns null`() {
    every { client.fetchInstruments() } throws RuntimeException("429 rate limited")
    val result = service.getInstrumentByTicker("SANe_EQ")
    expect(result).toEqual(null)
  }
}

class Trading212HoldingsServiceTest {
  private val etfClient = mockk<Trading212EtfClient>()
  private val enricher = mockk<Trading212HoldingEnricher>()
  private val service = Trading212HoldingsService(etfClient, enricher)

  @Test
  fun `should fetch holdings, assign ranks by weight descending, and enrich each`() {
    every { etfClient.getHoldings("BNKEp_EQ") } returns
      listOf(
        Trading212EtfHolding(ticker = "BBVAe_EQ", percentage = BigDecimal("10.42"), externalName = null),
        Trading212EtfHolding(ticker = "SANe_EQ", percentage = BigDecimal("13.94"), externalName = null),
        Trading212EtfHolding(ticker = "UCG", percentage = BigDecimal("9.18"), externalName = null),
      )
    every { enricher.enrich(any(), any()) } answers {
      val h = firstArg<Trading212EtfHolding>()
      val r = secondArg<Int>()
      HoldingData(name = h.ticker, ticker = h.ticker, sector = null, weight = h.percentage, rank = r, logoUrl = "url/${h.ticker}")
    }

    val results = service.fetchHoldings("BNKEp_EQ")

    expect(results.size).toEqual(3)
    expect(results[0].ticker).toEqual("SANe_EQ")
    expect(results[0].rank).toEqual(1)
    expect(results[0].weight).toEqualNumerically(BigDecimal("13.94"))
    expect(results[1].ticker).toEqual("BBVAe_EQ")
    expect(results[1].rank).toEqual(2)
    expect(results[2].ticker).toEqual("UCG")
    expect(results[2].rank).toEqual(3)
  }

  @Test
  fun `should return empty list when upstream returns empty`() {
    every { etfClient.getHoldings("BNKEp_EQ") } returns emptyList()

    val results = service.fetchHoldings("BNKEp_EQ")

    expect(results.size).toEqual(0)
  }

  @Test
  fun `should fetch TER from summary endpoint`() {
    every { etfClient.getSummary("BNKEp_EQ") } returns
      Trading212EtfSummary(
        description = null,
        dividendDistribution = null,
        expenseRatio = BigDecimal("0.3"),
        totalNetAssetValue = null,
        holdingsCount = 29,
      )

    val ter = service.fetchTer("BNKEp_EQ")

    expect(ter!!).toEqualNumerically(BigDecimal("0.3"))
  }
}
