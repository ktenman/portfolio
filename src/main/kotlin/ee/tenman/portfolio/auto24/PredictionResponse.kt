package ee.tenman.portfolio.auto24

data class PredictionResponse(
  val uuid: String,
  val prediction: String,
  val confidence: Double,
  val processingTimeMs: Double,
)
