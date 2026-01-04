package ee.tenman.portfolio.service.logo

import java.util.UUID

data class HoldingCandidateData(
  val holdingUuid: UUID,
  val companyName: String,
  val ticker: String?,
  val candidates: List<LogoCandidate>,
  val imageData: Map<Int, ByteArray>,
)
