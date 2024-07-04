package ee.tenman.portfolio.alphavantage

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.util.*

data class AlphaVantageResponse(
  @JsonProperty("Monthly Time Series")
  val monthlyTimeSeries: TreeMap<String, AlphaVantageDayData>? = null,

  @JsonProperty("Time Series (Daily)")
  val dailyTimeSeries: TreeMap<String, AlphaVantageDayData>? = null,

  @JsonProperty("Information")
  val information: String? = null,

  @JsonProperty("Error Message")
  val errorMessage: String? = null
) {
  data class AlphaVantageDayData(
    @JsonProperty("1. open")
    val open: BigDecimal,

    @JsonProperty("2. high")
    val high: BigDecimal,

    @JsonProperty("3. low")
    val low: BigDecimal,

    @JsonProperty("4. close")
    val close: BigDecimal,

    @JsonProperty("5. volume")
    val volume: Long
  )
}
