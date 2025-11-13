package ee.tenman.portfolio.wisdomtree

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class WisdomTreeHoldingsServiceTest {
  private val wisdomTreeHoldingsClient: WisdomTreeHoldingsClient = mockk()
  private val service = WisdomTreeHoldingsService(wisdomTreeHoldingsClient)

  @Test
  fun `should parse holdings HTML correctly`() {
    val htmlContent =
      """
      <table class="table table-striped-customized">
        <thead>
          <tr>
            <th>Name</th>
            <th></th>
            <th>Country Code</th>
            <th>Weight</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>1. SK Hynix Inc</td>
            <td>000660 KS</td>
            <td>KR </td>
            <td>4.24%</td>
          </tr>
          <tr>
            <td>2. Micron Technology Inc</td>
            <td>MU UQ</td>
            <td>US </td>
            <td>4.02%</td>
          </tr>
          <tr>
            <td>3. Advanced Micro Devices</td>
            <td>AMD US</td>
            <td>US </td>
            <td>3.11%</td>
          </tr>
        </tbody>
      </table>
      """.trimIndent()

    val holdings = service.parseHoldings(htmlContent)

    expect(holdings).toHaveSize(3)

    expect(holdings[0].name).toEqual("SK Hynix Inc")
    expect(holdings[0].ticker).toEqual("000660 KS")
    expect(holdings[0].countryCode).toEqual("KR")
    expect(holdings[0].weight).toEqualNumerically(BigDecimal("4.24"))

    expect(holdings[1].name).toEqual("Micron Technology Inc")
    expect(holdings[1].ticker).toEqual("MU UQ")
    expect(holdings[1].countryCode).toEqual("US")
    expect(holdings[1].weight).toEqualNumerically(BigDecimal("4.02"))

    expect(holdings[2].name).toEqual("Advanced Micro Devices")
    expect(holdings[2].ticker).toEqual("AMD US")
    expect(holdings[2].countryCode).toEqual("US")
    expect(holdings[2].weight).toEqualNumerically(BigDecimal("3.11"))
  }

  @Test
  fun `should extract ticker symbol from full ticker string`() {
    expect(service.extractTickerSymbol("NVDA UQ")).toEqual("NVDA")
    expect(service.extractTickerSymbol("AMD US")).toEqual("AMD")
    expect(service.extractTickerSymbol("000660 KS")).toEqual("000660")
    expect(service.extractTickerSymbol("2330 TT")).toEqual("2330")
  }

  @Test
  fun `should handle empty table`() {
    val htmlContent =
      """
      <table class="table table-striped-customized">
        <tbody></tbody>
      </table>
      """.trimIndent()

    val holdings = service.parseHoldings(htmlContent)

    expect(holdings).toHaveSize(0)
  }
}
