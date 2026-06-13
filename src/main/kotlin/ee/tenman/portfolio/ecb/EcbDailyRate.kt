package ee.tenman.portfolio.ecb

import java.math.BigDecimal
import java.time.LocalDate

data class EcbDailyRate(
  val date: LocalDate,
  val rate: BigDecimal,
)
