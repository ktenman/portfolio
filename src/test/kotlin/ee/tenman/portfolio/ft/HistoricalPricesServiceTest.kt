package ee.tenman.portfolio.ft

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class HistoricalPricesServiceTest {
  @Mock
  private lateinit var historicalPricesClient: HistoricalPricesClient

  private lateinit var service: HistoricalPricesService

  @BeforeEach
  fun setUp() {
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
  )
  fun `should map ticker symbols correctly`(
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

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        org.mockito.kotlin.eq(expectedTickerId),
      ),
    ).thenReturn(mockResponse)

    val result = service.fetchPrices(symbol)

    assertThat(result).isNotEmpty
    assertThat(result).containsKey(LocalDate.of(2025, 1, 17))
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

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        org.mockito.kotlin.eq(unknownSymbol),
      ),
    ).thenReturn(mockResponse)

    val result = service.fetchPrices(unknownSymbol)

    assertThat(result).isNotEmpty
  }

  @Test
  fun `should parse XAIX price data correctly`() {
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

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(HistoricalPricesResponse(html = xaixHtml))

    val result = service.fetchAndParsePrices("2025/01/16", "2025/01/17", "515873934")

    val jan17Data = result[LocalDate.of(2025, 1, 17)]
    assertThat(jan17Data).isNotNull
    assertThat(jan17Data?.open).isEqualTo(BigDecimal("137.40"))
    assertThat(jan17Data?.high).isEqualTo(BigDecimal("139.60"))
    assertThat(jan17Data?.low).isEqualTo(BigDecimal("137.30"))
    assertThat(jan17Data?.close).isEqualTo(BigDecimal("138.80"))
    assertThat(jan17Data?.volume).isEqualTo(22839L)

    val jan16Data = result[LocalDate.of(2025, 1, 16)]
    assertThat(jan16Data).isNotNull
    assertThat(jan16Data?.open).isEqualTo(BigDecimal("137.90"))
    assertThat(jan16Data?.close).isEqualTo(BigDecimal("137.66"))
    assertThat(jan16Data?.volume).isEqualTo(44583L)
  }

  @Test
  fun `should handle volume with k suffix`() {
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

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(HistoricalPricesResponse(html = htmlWithKVolume))

    val result = service.fetchAndParsePrices("2025/01/06", "2025/01/06", "515873934")

    val data = result[LocalDate.of(2025, 1, 6)]
    assertThat(data).isNotNull
    assertThat(data?.volume).isEqualTo(71420L)
  }

  @Test
  fun `should handle empty response`() {
    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(HistoricalPricesResponse(html = ""))

    val result = service.fetchAndParsePrices("2025/01/01", "2025/01/31", "515873934")

    assertThat(result).isEmpty()
  }

  @Test
  fun `should handle malformed HTML gracefully`() {
    val malformedHtml = """<tr><td>Invalid</td></tr>"""

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(HistoricalPricesResponse(html = malformedHtml))

    val result = service.fetchAndParsePrices("2025/01/01", "2025/01/31", "515873934")

    assertThat(result).isEmpty()
  }

  @Test
  fun `should parse prices with comma separators`() {
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

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(HistoricalPricesResponse(html = htmlWithCommas))

    val result = service.fetchAndParsePrices("2025/01/15", "2025/01/15", "515873934")

    val data = result[LocalDate.of(2025, 1, 15)]
    assertThat(data).isNotNull
    assertThat(data?.open).isEqualTo(BigDecimal("1234.56"))
    assertThat(data?.high).isEqualTo(BigDecimal("1240.00"))
    assertThat(data?.low).isEqualTo(BigDecimal("1230.00"))
    assertThat(data?.close).isEqualTo(BigDecimal("1238.50"))
    assertThat(data?.volume).isEqualTo(123456L)
  }

  @Test
  fun `should validate XAIX ticker ID is correct`() {
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

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        org.mockito.kotlin.eq(expectedTickerId),
      ),
    ).thenReturn(mockResponse)

    val result = service.fetchPrices(xaixSymbol)

    assertThat(result).isNotEmpty
    val priceData = result[LocalDate.of(2025, 1, 17)]
    assertThat(priceData).isNotNull
    assertThat(priceData?.open).isEqualTo(BigDecimal("137.40"))
    assertThat(priceData?.high).isEqualTo(BigDecimal("139.60"))
    assertThat(priceData?.low).isEqualTo(BigDecimal("137.30"))
    assertThat(priceData?.close).isEqualTo(BigDecimal("138.80"))
    assertThat(priceData?.volume).isEqualTo(22839L)
  }
}
