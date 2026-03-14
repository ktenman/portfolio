package ee.tenman.portfolio.dto

import java.io.Serializable
import java.time.LocalDate

data class ComparisonResponse(
  val instruments: List<InstrumentComparisonDto>,
  val startDate: LocalDate,
  val endDate: LocalDate,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
