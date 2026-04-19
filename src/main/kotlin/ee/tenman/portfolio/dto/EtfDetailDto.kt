package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.Currency
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
  val fundCurrency: Currency? = null,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
