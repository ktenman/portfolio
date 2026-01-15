package ee.tenman.portfolio.dto

import java.io.Serializable
import java.math.BigDecimal

data class ConcentrationDto(
  val top10Percentage: BigDecimal,
  val largestPosition: LargestPositionDto?,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
