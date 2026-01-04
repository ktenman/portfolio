package ee.tenman.portfolio.dto

data class LogoCandidateDto(
  val thumbnailUrl: String,
  val title: String,
  val index: Int,
  val imageDataUrl: String? = null,
)
