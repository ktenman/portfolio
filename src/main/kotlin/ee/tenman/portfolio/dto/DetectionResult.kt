package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.DetectionProvider

data class DetectionResult(
  val plateNumber: String?,
  val hasCar: Boolean,
  val provider: DetectionProvider,
)
