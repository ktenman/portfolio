package ee.tenman.portfolio.dto

import java.math.BigDecimal
import java.time.LocalDate

data class XirrWindowDto(
  val period: String,
  val fromDate: LocalDate?,
  val xirr: BigDecimal?,
)
