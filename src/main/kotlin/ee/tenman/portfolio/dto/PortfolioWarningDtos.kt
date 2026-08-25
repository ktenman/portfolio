package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.Currency
import java.io.Serializable
import java.math.BigDecimal

enum class PortfolioWarningRule(
  val label: String,
  val threshold: BigDecimal,
) {
  LARGEST_HOLDING("Largest holding", BigDecimal("10")),
  SECTOR_CONCENTRATION("Largest sector", BigDecimal("35")),
  COUNTRY_CONCENTRATION("Largest country", BigDecimal("70")),
  PLATFORM_CONCENTRATION("Largest platform", BigDecimal("60")),
  AVERAGE_TER("Weighted TER", BigDecimal("0.40")),
  CURRENCY_EXPOSURE("Largest non-EUR fund currency", BigDecimal("60")),
}

data class FundValue(
  val value: BigDecimal,
  val ter: BigDecimal?,
  val currency: Currency?,
)

data class PortfolioWarningDto(
  val rule: PortfolioWarningRule,
  val label: String,
  val detail: String?,
  val measuredPercentage: BigDecimal,
  val thresholdPercentage: BigDecimal,
  val breached: Boolean,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
