package ee.tenman.portfolio.dto

import java.io.Serializable
import java.math.BigDecimal

data class EtfDetailDto(
  val instrumentId: Long,
  val symbol: String,
  val name: String,
  val allocation: BigDecimal,
  val ter: BigDecimal?,
  val annualReturn: BigDecimal?,
  val currentPrice: BigDecimal?,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
