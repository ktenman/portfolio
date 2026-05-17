package ee.tenman.portfolio.service.etf

data class HoldingMatchCandidate(
  val firstHoldingId: Long,
  val firstName: String,
  val firstTicker: String?,
  val secondHoldingId: Long,
  val secondName: String,
  val secondTicker: String?,
  val source: MatchSource,
)
