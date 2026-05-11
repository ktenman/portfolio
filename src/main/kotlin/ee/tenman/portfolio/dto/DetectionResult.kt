package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.VisionModel

data class DetectionResult(
  val plateNumber: String? = null,
  val provider: VisionModel? = null,
)
