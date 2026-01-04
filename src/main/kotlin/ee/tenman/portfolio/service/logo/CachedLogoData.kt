package ee.tenman.portfolio.service.logo

import java.io.Serializable

data class CachedLogoData(
  val candidates: List<LogoCandidate>,
  val images: Map<Int, ByteArray>,
) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
