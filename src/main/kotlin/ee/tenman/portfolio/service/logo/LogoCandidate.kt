package ee.tenman.portfolio.service.logo

import java.io.Serializable

data class LogoCandidate(
  val imageUrl: String,
  val thumbnailUrl: String,
  val title: String,
  val index: Int,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
