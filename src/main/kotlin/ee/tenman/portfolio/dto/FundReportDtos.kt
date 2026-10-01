package ee.tenman.portfolio.dto

import java.math.BigDecimal
import java.time.LocalDate

data class FundWeightDto(
  val isin: String,
  val name: String,
  val weight: BigDecimal,
)

data class FundReportDto(
  val asOfDate: LocalDate,
  val funds: List<FundWeightDto>,
)
