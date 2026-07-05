package ee.tenman.portfolio.service.integration

data class BatchClassificationOutcome<T>(
  val results: Map<Long, T>,
  val llmAnswered: Boolean,
)
