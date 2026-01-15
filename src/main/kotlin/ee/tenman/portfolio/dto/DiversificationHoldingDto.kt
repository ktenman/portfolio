package ee.tenman.portfolio.dto

import java.io.Serializable
import java.math.BigDecimal

data class DiversificationHoldingDto(
  val name: String,
  val ticker: String?,
  val percentage: BigDecimal,
  val inEtfs: String,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
