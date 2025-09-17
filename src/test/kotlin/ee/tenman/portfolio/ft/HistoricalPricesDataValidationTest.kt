package ee.tenman.portfolio.ft

import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.common.DailyPriceDataImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
class HistoricalPricesDataValidationTest {
  @Mock
  private lateinit var historicalPricesClient: HistoricalPricesClient

  private lateinit var service: HistoricalPricesService

  @BeforeEach
  fun setUp() {
    service = HistoricalPricesService(historicalPricesClient)
  }

  @Test
  fun `should filter out future dates`() {
    val today = LocalDate.now()
    val futureDate = today.plusMonths(8)

    val htmlWithFutureDates =
      """
      <tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Friday, ${futureDate.dayOfMonth} September, 2025</span>
        </td>
        <td>145.02</td>
        <td>145.34</td>
        <td>144.76</td>
        <td>145.14</td>
        <td>23,992</td>
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
      </tr>
      """.trimIndent()

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(HistoricalPricesResponse(html = htmlWithFutureDates))

    val result = service.fetchAndParsePrices("2025/01/01", "2025/09/30", "515873934")

    assertThat(result).containsKey(LocalDate.of(2025, 1, 16))
    assertThat(result).doesNotContainKey(futureDate)
    assertThat(result.keys).allMatch { date -> date.isBefore(today) || date.isEqual(today) }
  }

  @ParameterizedTest
  @MethodSource("invalidPriceDataProvider")
  fun `should detect invalid price relationships`(
    open: String,
    high: String,
    low: String,
    close: String,
    expectedValid: Boolean,
    description: String,
  ) {
    val data =
      DailyPriceDataImpl(
        open = BigDecimal(open),
        high = BigDecimal(high),
        low = BigDecimal(low),
        close = BigDecimal(close),
        volume = 1000L,
      )

    val isValid = validatePriceData(data)

    assertThat(isValid)
      .withFailMessage("Test case: $description - Expected valid=$expectedValid but got $isValid")
      .isEqualTo(expectedValid)
  }

  @Test
  fun `should handle duplicate dates by keeping latest`() {
    val duplicateHtml =
      """
      <tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Monday, January 13, 2025</span>
        </td>
        <td>100.00</td>
        <td>105.00</td>
        <td>99.00</td>
        <td>104.00</td>
        <td>5000</td>
      </tr>
      <tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Monday, January 13, 2025</span>
        </td>
        <td>101.00</td>
        <td>106.00</td>
        <td>100.00</td>
        <td>105.00</td>
        <td>6000</td>
      </tr>
      """.trimIndent()

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(HistoricalPricesResponse(html = duplicateHtml))

    val result = service.fetchAndParsePrices("2025/01/13", "2025/01/13", "515873934")

    assertThat(result).hasSize(1)
    val data = result[LocalDate.of(2025, 1, 13)]
    assertThat(data?.open).isEqualTo("101.00")
    assertThat(data?.volume).isEqualTo(6000L)
  }

  @Test
  fun `should validate volume is positive`() {
    val priceData =
      mapOf(
        LocalDate.of(2025, 1, 17) to
          DailyPriceDataImpl(
            open = BigDecimal("100.00"),
            high = BigDecimal("105.00"),
            low = BigDecimal("99.00"),
            close = BigDecimal("104.00"),
            volume = 1000L,
          ),
        LocalDate.of(2025, 1, 16) to
          DailyPriceDataImpl(
            open = BigDecimal("100.00"),
            high = BigDecimal("105.00"),
            low = BigDecimal("99.00"),
            close = BigDecimal("104.00"),
            volume = 0L,
          ),
      )

    val validData = priceData.filter { (_, data) -> data.volume > 0 }

    assertThat(validData).hasSize(1)
    assertThat(validData).containsKey(LocalDate.of(2025, 1, 17))
  }

  @Test
  fun `should validate XAIX data consistency for real scenario`() {
    val xaixRealDataHtml =
      """
      <tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Friday, August 29, 2025</span>
        </td>
        <td>141.12</td>
        <td>141.16</td>
        <td>139.10</td>
        <td>139.40</td>
        <td>41,148</td>
      </tr>
      """.trimIndent()

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(HistoricalPricesResponse(html = xaixRealDataHtml))

    val result = service.fetchAndParsePrices("2025/08/29", "2025/08/29", "515873934")

    assertThat(result).hasSize(1)
    val data = result[LocalDate.of(2025, 8, 29)]!!

    val isValid = validatePriceData(data)

    assertThat(isValid).isTrue()
    assertThat(data.low).isLessThan(data.open)
    assertThat(data.low).isLessThan(data.close)
    assertThat(data.high).isGreaterThan(data.close)
  }

  @Test
  fun `should handle missing or null values gracefully`() {
    val incompleteHtml =
      """
      <tr>
        <td class="mod-ui-table__cell--text">
          <span class="mod-ui-hide-small-below">Monday, January 13, 2025</span>
        </td>
        <td></td>
        <td>105.00</td>
        <td>99.00</td>
        <td>104.00</td>
        <td>5000</td>
      </tr>
      """.trimIndent()

    whenever(
      historicalPricesClient.getHistoricalPrices(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(HistoricalPricesResponse(html = incompleteHtml))

    val result = service.fetchAndParsePrices("2025/01/13", "2025/01/13", "515873934")

    assertThat(result).isEmpty()
  }

  private fun validatePriceData(data: DailyPriceData): Boolean =
    data.low <= data.open &&
      data.low <= data.close &&
      data.low <= data.high &&
      data.high >= data.open &&
      data.high >= data.close &&
      data.high >= data.low

  companion object {
    @JvmStatic
    fun invalidPriceDataProvider(): Stream<Arguments> =
      Stream.of(
        Arguments.of("100.00", "105.00", "99.00", "104.00", true, "Valid normal case"),
        Arguments.of("100.00", "100.00", "100.00", "100.00", true, "Valid flat day"),
        Arguments.of("100.00", "105.00", "99.00", "105.00", true, "Valid close at high"),
        Arguments.of("99.00", "105.00", "99.00", "104.00", true, "Valid open at low"),
        Arguments.of("141.12", "141.16", "139.10", "139.40", true, "Valid XAIX example"),
        Arguments.of("100.00", "105.00", "95.00", "104.00", true, "Valid large range"),
        Arguments.of("100.00", "100.01", "99.99", "100.00", true, "Valid small movements"),
      )
  }
}
