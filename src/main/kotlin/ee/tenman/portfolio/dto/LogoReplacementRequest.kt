package ee.tenman.portfolio.dto

import java.util.UUID

data class LogoReplacementRequest(
  val holdingUuid: UUID,
  val candidateIndex: Int,
)
