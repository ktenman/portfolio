package ee.tenman.portfolio.dto

import java.io.Serializable
import java.math.BigDecimal

data class LargestPositionDto(
  val name: String,
  val percentage: BigDecimal,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
