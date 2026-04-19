package ee.tenman.portfolio.lightyear

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class LightyearFundInfoResponse(
  val ter: BigDecimal? = null,
  val aum: BigDecimal? = null,
  val aumCurrency: String? = null,
  @JsonProperty("baseCurrency")
  val fundCurrency: String? = null,
)
