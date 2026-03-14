package ee.tenman.portfolio.dto

import java.time.LocalDate

data class ComparisonResponse(
  val instruments: List<InstrumentComparisonDto>,
  val startDate: LocalDate,
  val endDate: LocalDate,
)
