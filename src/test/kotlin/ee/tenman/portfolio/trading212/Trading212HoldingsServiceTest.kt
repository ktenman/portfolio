package ee.tenman.portfolio.trading212

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.dto.HoldingData
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal

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
