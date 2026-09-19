package ee.tenman.portfolio.service.summary

import java.time.LocalDate

enum class XirrWindowDefinition(
  val label: String,
  val start: (LocalDate) -> LocalDate,
) {
  ONE_MONTH("1M", { it.minusMonths(1) }),
  THREE_MONTHS("3M", { it.minusMonths(3) }),
  SIX_MONTHS("6M", { it.minusMonths(6) }),
  YEAR_TO_DATE("YTD", { it.withDayOfYear(1) }),
  ONE_YEAR("1Y", { it.minusYears(1) }),
  TWO_YEARS("2Y", { it.minusYears(2) }),
  THREE_YEARS("3Y", { it.minusYears(3) }),
  FIVE_YEARS("5Y", { it.minusYears(5) }),
}
