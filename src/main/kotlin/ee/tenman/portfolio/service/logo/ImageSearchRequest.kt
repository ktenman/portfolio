package ee.tenman.portfolio.service.logo

data class ImageSearchRequest(
  val query: String,
  val maxResults: Int = 5,
  val squareOnly: Boolean = true,
)
