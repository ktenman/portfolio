package ee.tenman.portfolio.lightyear

import java.math.BigDecimal

data class LightyearFundInfoResponse(
  val ter: BigDecimal? = null,
  val aum: BigDecimal? = null,
  val aumCurrency: String? = null,
)
