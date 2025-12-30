package ee.tenman.portfolio.service.logo

data class ImageSearchResponse(
  val success: Boolean,
  val results: List<ImageResult> = emptyList(),
  val error: String? = null,
)

data class ImageResult(
  val image: String,
  val thumbnail: String,
  val title: String,
  val width: Int,
  val height: Int,
)
