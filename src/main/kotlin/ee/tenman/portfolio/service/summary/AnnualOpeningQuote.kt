package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.model.InstrumentSnapshot
import java.math.BigDecimal
import java.time.LocalDate

data class AnnualOpeningQuote(
  val snapshot: InstrumentSnapshot,
  val priceDate: LocalDate,
  val price: BigDecimal,
)
