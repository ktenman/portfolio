package ee.tenman.portfolio.trading212

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class Trading212Response(
  val data: Map<String, Trading212PriceData>,
)

data class Trading212PriceData(
  @JsonProperty("b") val bid: BigDecimal,
  @JsonProperty("s") val spread: BigDecimal,
  @JsonProperty("t") val timestamp: String,
)
