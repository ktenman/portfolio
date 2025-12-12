package ee.tenman.portfolio.model

data class InstrumentSnapshotsWithPortfolioXirr(
  val snapshots: List<InstrumentSnapshot>,
  val portfolioXirr: Double,
)
