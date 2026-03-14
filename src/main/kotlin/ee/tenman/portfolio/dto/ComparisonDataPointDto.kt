package ee.tenman.portfolio.dto

import java.time.LocalDate

data class ComparisonDataPointDto(
  val date: LocalDate,
  val percentageChange: Double,
)
