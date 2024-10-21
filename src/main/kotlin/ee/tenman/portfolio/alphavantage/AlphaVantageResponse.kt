package ee.tenman.portfolio.alphavantage

import com.fasterxml.jackson.annotation.JsonProperty
import ee.tenman.portfolio.common.DayData
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
    override val open: BigDecimal,

    @JsonProperty("2. high")
    override val high: BigDecimal,

    @JsonProperty("3. low")
    override val low: BigDecimal,

    @JsonProperty("4. close")
    override val close: BigDecimal,

    @JsonProperty("5. volume")
    override val volume: Long
  ) : DayData
}
