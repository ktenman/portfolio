package ee.tenman.portfolio.controller

import java.util.UUID

data class LogoReplacementRequest(
  val holdingUuid: UUID,
  val candidateIndex: Int,
)
