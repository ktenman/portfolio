package ee.tenman.portfolio.lightyear

import ee.tenman.portfolio.domain.Currency
import java.math.BigDecimal

data class LightyearFundInfoData(
  val ter: BigDecimal?,
  val fundCurrency: Currency?,
)
