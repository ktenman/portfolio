package ee.tenman.portfolio.dto

import java.math.BigDecimal
import java.time.LocalDate

data class AnnualWindowDto(
  val period: String,
  val fromDate: LocalDate?,
  val annualReturn: BigDecimal?,
)
