package ee.tenman.portfolio.scheduler

enum class MarketPhase(
  val minIntervalSeconds: Long,
  val maxIntervalSeconds: Long,
  val defaultIntervalSeconds: Long,
) {
  MAIN_MARKET_HOURS(60, 300, 60),
  PRE_POST_MARKET(900, 1800, 900),
  OFF_HOURS(7200, 14400, 7200),
  WEEKEND(14400, 28800, 14400),
}
