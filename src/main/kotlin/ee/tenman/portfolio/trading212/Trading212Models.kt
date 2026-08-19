package ee.tenman.portfolio.trading212

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trading212EtfHolding(
  val ticker: String,
  val percentage: BigDecimal,
  val externalName: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trading212EtfSummary(
  val description: String?,
  val dividendDistribution: String?,
  val expenseRatio: BigDecimal?,
  val totalNetAssetValue: BigDecimal?,
  val holdingsCount: Int?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trading212Instrument(
  val ticker: String,
  val type: String,
  val isin: String?,
  val currencyCode: String?,
  val name: String?,
  val shortName: String?,
)

data class Trading212Response(
  val data: Map<String, Trading212PriceData>,
)

data class Trading212PriceData(
  @JsonProperty("b") val bid: BigDecimal,
  @JsonProperty("s") val spread: BigDecimal,
  @JsonProperty("t") val timestamp: String,
)
