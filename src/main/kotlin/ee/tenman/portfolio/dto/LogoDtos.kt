package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.VisionModel
import java.util.UUID

data class LogoCandidateDto(
  val thumbnailUrl: String,
  val title: String,
  val index: Int,
  val imageDataUrl: String? = null,
)

data class LogoReplacementRequest(
  val holdingUuid: UUID,
  val candidateIndex: Int,
)

data class PrefetchRequest(
  val holdingUuids: List<UUID>,
)

data class DetectionResult(
  val plateNumber: String? = null,
  val provider: VisionModel? = null,
)
