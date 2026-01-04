package ee.tenman.portfolio.service.logo

import java.util.UUID

data class BatchLogoValidationResult(
  val holdingUuid: UUID,
  val companyName: String,
  val ticker: String?,
  val validCandidateIndices: List<Int>,
  val allCandidates: List<LogoCandidate>,
  val imageData: Map<Int, ByteArray>,
)
