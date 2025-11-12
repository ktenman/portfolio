package ee.tenman.portfolio.ft

import ch.tutteli.atrium.api.fluent.en_GB.notToBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class HistoricalPricesServiceTest {
  private lateinit var historicalPricesClient: HistoricalPricesClient

  private lateinit var service: HistoricalPricesService

  @BeforeEach
  fun setUp() {
    historicalPricesClient = mockk()
    val clock =
      Clock.fixed(
      LocalDate.of(2025, 1, 20).atStartOfDay(ZoneId.of("UTC")).toInstant(),
      ZoneId.of("UTC"),
    )
    service = HistoricalPricesService(historicalPricesClient, clock)
  }

  @ParameterizedTest
  @CsvSource(
    "QDVE.DEX,93501088",
    "QDVE:GER:EUR,93500326",
    "XAIX:GER:EUR,515873934",
    "VUAA:GER:EUR,573788032",
    "WTAI:MIL:EUR,505821605",
  )
  fun `should map ticker symbols correctly when given different symbol formats`(
    symbol: String,
    expectedTickerId: String,
  ) {
    val mockResponse =
      HistoricalPricesResponse(
        html =
          """<tr>
          <td class="mod-ui-table__cell--text">
            <span class="mod-ui-hide-small-below">Friday, January 17, 2025</span>
          </td>
          <td>100.00</td>
          <td>105.00</td>
          <td>99.50</td>
          <td>104.75</td>
          <td>1000</td>
        </tr>""",
      )

    every {
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        expectedTickerId,
      )
    } returns mockResponse

    val result = service.fetchPrices(symbol)

    expect(result).notToBeEmpty()
    expect(result.keys).toContain(LocalDate.of(2025, 1, 17))
  }

  @Test
  fun `should use symbol as ticker when no mapping exists`() {
    val unknownSymbol = "UNKNOWN:SYMBOL"
    val mockResponse =
      HistoricalPricesResponse(
        html =
          """<tr>
          <td class="mod-ui-table__cell--text">
            <span class="mod-ui-hide-small-below">Monday, January 13, 2025</span>
          </td>
          <td>50.00</td>
          <td>52.00</td>
          <td>49.00</td>
          <td>51.50</td>
          <td>5000</td>
        </tr>""",
      )

    every {
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        unknownSymbol,
      )
    } returns mockResponse

    val result = service.fetchPrices(unknownSymbol)

    expect(result).notToBeEmpty()
  }

  @Test
  fun `should parse XAIX price data correctly when fetching multiple days`() {
    val xaixHtml =
      """<tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Friday, January 17, 2025</span>
        </td>
        <td>137.40</td>
        <td>139.60</td>
        <td>137.30</td>
        <td>138.80</td>
        <td>22,839</td>
      </tr>
      <tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Thursday, January 16, 2025</span>
        </td>
        <td>137.90</td>
        <td>138.28</td>
        <td>137.26</td>
        <td>137.66</td>
        <td>44,583</td>
      </tr>"""

    every {
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      )
    } returns HistoricalPricesResponse(html = xaixHtml)

    val result = service.fetchAndParsePrices("2025/01/16", "2025/01/17", "515873934")

    val jan17Data = result[LocalDate.of(2025, 1, 17)]
    expect(jan17Data).notToEqualNull()
    expect(jan17Data?.open).notToEqualNull().toEqualNumerically(BigDecimal("137.40"))
    expect(jan17Data?.high).notToEqualNull().toEqualNumerically(BigDecimal("139.60"))
    expect(jan17Data?.low).notToEqualNull().toEqualNumerically(BigDecimal("137.30"))
    expect(jan17Data?.close).notToEqualNull().toEqualNumerically(BigDecimal("138.80"))
    expect(jan17Data?.volume).toEqual(22839L)

    val jan16Data = result[LocalDate.of(2025, 1, 16)]
    expect(jan16Data).notToEqualNull()
    expect(jan16Data?.open).notToEqualNull().toEqualNumerically(BigDecimal("137.90"))
    expect(jan16Data?.close).notToEqualNull().toEqualNumerically(BigDecimal("137.66"))
    expect(jan16Data?.volume).toEqual(44583L)
  }

  @Test
  fun `should handle volume with k suffix when parsing price data`() {
    val htmlWithKVolume =
      """<tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Monday, January 06, 2025</span>
        </td>
        <td>138.16</td>
        <td>139.58</td>
        <td>137.80</td>
        <td>139.26</td>
        <td>71.42k</td>
      </tr>"""

    every {
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      )
    } returns HistoricalPricesResponse(html = htmlWithKVolume)

    val result = service.fetchAndParsePrices("2025/01/06", "2025/01/06", "515873934")

    val data = result[LocalDate.of(2025, 1, 6)]
    expect(data).notToEqualNull()
    expect(data?.volume).toEqual(71420L)
  }

  @Test
  fun `should handle volume with m suffix when parsing price data`() {
    val htmlWithMVolume =
      """<tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Tuesday, March 03, 2020</span>
        </td>
        <td>100.00</td>
        <td>105.00</td>
        <td>99.50</td>
        <td>104.75</td>
        <td>2.5m</td>
      </tr>"""

    every {
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      )
    } returns HistoricalPricesResponse(html = htmlWithMVolume)

    val result = service.fetchAndParsePrices("2020/03/03", "2020/03/03", "573788032")

    val data = result[LocalDate.of(2020, 3, 3)]
    expect(data).notToEqualNull()
    expect(data?.volume).toEqual(2_500_000L)
  }

  @Test
  fun `should handle volume with b suffix when parsing price data`() {
    val htmlWithBVolume =
      """<tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Wednesday, March 11, 2020</span>
        </td>
        <td>100.00</td>
        <td>105.00</td>
        <td>99.50</td>
        <td>104.75</td>
        <td>1.5b</td>
      </tr>"""

    every {
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      )
    } returns HistoricalPricesResponse(html = htmlWithBVolume)

    val result = service.fetchAndParsePrices("2020/03/11", "2020/03/11", "573788032")

    val data = result[LocalDate.of(2020, 3, 11)]
    expect(data).notToEqualNull()
    expect(data?.volume).toEqual(1_500_000_000L)
  }

  @Test
  fun `should return empty map when response is empty`() {
    every {
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      )
    } returns HistoricalPricesResponse(html = "")

    val result = service.fetchAndParsePrices("2025/01/01", "2025/01/31", "515873934")

    expect(result).toBeEmpty()
  }

  @Test
  fun `should handle malformed HTML gracefully when parsing prices`() {
    val malformedHtml = """<tr><td>Invalid</td></tr>"""

    every {
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      )
    } returns HistoricalPricesResponse(html = malformedHtml)

    val result = service.fetchAndParsePrices("2025/01/01", "2025/01/31", "515873934")

    expect(result).toBeEmpty()
  }

  @Test
  fun `should parse prices with comma separators when fetching data`() {
    val htmlWithCommas =
      """<tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Wednesday, January 15, 2025</span>
        </td>
        <td>1,234.56</td>
        <td>1,240.00</td>
        <td>1,230.00</td>
        <td>1,238.50</td>
        <td>123,456</td>
      </tr>"""

    every {
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      )
    } returns HistoricalPricesResponse(html = htmlWithCommas)

    val result = service.fetchAndParsePrices("2025/01/15", "2025/01/15", "515873934")

    val data = result[LocalDate.of(2025, 1, 15)]
    expect(data).notToEqualNull()
    expect(data?.open).notToEqualNull().toEqualNumerically(BigDecimal("1234.56"))
    expect(data?.high).notToEqualNull().toEqualNumerically(BigDecimal("1240.00"))
    expect(data?.low).notToEqualNull().toEqualNumerically(BigDecimal("1230.00"))
    expect(data?.close).notToEqualNull().toEqualNumerically(BigDecimal("1238.50"))
    expect(data?.volume).toEqual(123456L)
  }

  @Test
  fun `should validate XAIX ticker ID is correct when fetching prices`() {
    val xaixSymbol = "XAIX:GER:EUR"
    val expectedTickerId = "515873934"

    val mockResponse =
      HistoricalPricesResponse(
        html =
          """<tr>
          <td class="mod-ui-table__cell--text">
            <span class="mod-ui-hide-small-below">Friday, January 17, 2025</span>
          </td>
          <td>137.40</td>
          <td>139.60</td>
          <td>137.30</td>
          <td>138.80</td>
          <td>22,839</td>
        </tr>""",
      )

    every {
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        expectedTickerId,
      )
    } returns mockResponse

    val result = service.fetchPrices(xaixSymbol)

    expect(result).notToBeEmpty()
    val priceData = result[LocalDate.of(2025, 1, 17)]
    expect(priceData).notToEqualNull()
    expect(priceData?.open).notToEqualNull().toEqualNumerically(BigDecimal("137.40"))
    expect(priceData?.high).notToEqualNull().toEqualNumerically(BigDecimal("139.60"))
    expect(priceData?.low).notToEqualNull().toEqualNumerically(BigDecimal("137.30"))
    expect(priceData?.close).notToEqualNull().toEqualNumerically(BigDecimal("138.80"))
    expect(priceData?.volume).toEqual(22839L)
  }
}
