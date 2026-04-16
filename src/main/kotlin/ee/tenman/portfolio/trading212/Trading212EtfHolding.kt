package ee.tenman.portfolio.trading212

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trading212EtfHolding(
  val ticker: String,
  val percentage: BigDecimal,
  val externalName: String?,
)
