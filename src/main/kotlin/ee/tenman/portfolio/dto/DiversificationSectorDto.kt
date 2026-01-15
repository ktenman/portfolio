package ee.tenman.portfolio.dto

import java.io.Serializable
import java.math.BigDecimal

data class DiversificationSectorDto(
  val sector: String,
  val percentage: BigDecimal,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
