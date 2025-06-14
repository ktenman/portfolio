package ee.tenman.portfolio.alphavantage

import com.fasterxml.jackson.annotation.JsonProperty
import ee.tenman.portfolio.common.DailyPriceData
import java.math.BigDecimal
import java.util.TreeMap

data class AlphaVantageResponse(
  @JsonProperty("Monthly Time Series")
  val monthlyTimeSeries: TreeMap<String, AlphaVantageDailyPriceData>? = null,
  @JsonProperty("Time Series (Daily)")
  val dailyTimeSeries: TreeMap<String, AlphaVantageDailyPriceData>? = null,
  @JsonProperty("Information")
  val information: String? = null,
  @JsonProperty("Error Message")
  val errorMessage: String? = null,
) {
  data class AlphaVantageDailyPriceData(
    @JsonProperty("1. open")
    override val open: BigDecimal,
    @JsonProperty("2. high")
    override val high: BigDecimal,
    @JsonProperty("3. low")
    override val low: BigDecimal,
    @JsonProperty("4. close")
    override val close: BigDecimal,
    @JsonProperty("5. volume")
    override val volume: Long,
  ) : DailyPriceData
}
