package ee.tenman.portfolio.dto

import java.math.BigDecimal
import java.time.LocalDate

data class XirrWindowDto(
  val period: String,
  val fromDate: LocalDate?,
  val xirr: BigDecimal?,
)

data class XirrWindowsDto(
  val windows: List<XirrWindowDto>,
)

data class AnnualWindowDto(
  val period: String,
  val fromDate: LocalDate?,
  val annualReturn: BigDecimal?,
)

data class AnnualWindowsDto(
  val windows: List<AnnualWindowDto>,
)
