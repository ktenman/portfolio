package ee.tenman.portfolio.blackrock

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CsusHoldingsServiceTest {
  private val client = mockk<BlackRockHoldingsClient>()
  private val service = CsusHoldingsService(client)

  private val header =
    "Ticker,Name,Sector,Asset Class,Market Value,Weight (%),Notional Value,Shares,Price,Location,Exchange,Market Currency"

  private fun row(
    ticker: String,
    weight: String,
  ): String =
    "\"$ticker\",\"$ticker CORP\",\"Information Technology\",\"Equity\",\"1\",\"$weight\",\"1\",\"1\"," +
      "\"1.00\",\"United States\",\"NASDAQ\",\"USD\""

  @Test
  fun `should return holdings ranked by descending weight`() {
    every { client.getHoldingsCsv(any(), any(), any(), any()) } returns
      listOf("Fund Holdings as of,\"11/Jun/2026\"", " ", header, row("AAPL", "6.81"), row("NVDA", "7.42"))
        .joinToString("\n")

    val holdings = service.fetchHoldings()

    expect(holdings).toHaveSize(2)
    expect(holdings.first().ticker).toEqual("NVDA")
    expect(holdings.first().rank).toEqual(1)
    expect(holdings.first().weight).toEqualNumerically(BigDecimal("7.42"))
    expect(holdings[1].ticker).toEqual("AAPL")
    expect(holdings[1].rank).toEqual(2)
  }

  @Test
  fun `should return empty list when csv has no equity rows`() {
    every { client.getHoldingsCsv(any(), any(), any(), any()) } returns
      listOf("Fund Holdings as of,\"11/Jun/2026\"", " ", header).joinToString("\n")

    val holdings = service.fetchHoldings()

    expect(holdings).toHaveSize(0)
  }
}
