package ee.tenman.portfolio.service.summary

import java.time.LocalDate

data class XirrWindowDefinition(
  val label: String,
  val startDateFor: (LocalDate) -> LocalDate,
)
