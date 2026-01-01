package ee.tenman.portfolio.dto

import java.io.Serializable
import java.math.BigDecimal
import java.util.UUID

data class EtfHoldingBreakdownDto(
  val holdingUuid: UUID?,
  val holdingTicker: String?,
  val holdingName: String,
  val percentageOfTotal: BigDecimal,
  val totalValueEur: BigDecimal,
  val holdingSector: String?,
  val holdingCountryCode: String?,
  val holdingCountryName: String?,
  val inEtfs: String,
  val numEtfs: Int,
  val platforms: String,
) : Serializable {
  companion object {
    private const val serialVersionUID = 4L
  }
}
