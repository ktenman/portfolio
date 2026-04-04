package ee.tenman.portfolio.dto

import java.io.Serializable
import java.time.LocalDate

data class ComparisonDataPointDto(
  val date: LocalDate,
  val percentageChange: Double,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
