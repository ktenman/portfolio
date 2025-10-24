package ee.tenman.portfolio.dto

import java.io.Serializable
import java.math.BigDecimal

data class EtfHoldingBreakdownDto(
  val holdingTicker: String?,
  val holdingName: String,
  val percentageOfTotal: BigDecimal,
  val totalValueEur: BigDecimal,
  val holdingSector: String?,
  val inEtfs: String,
  val numEtfs: Int,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
