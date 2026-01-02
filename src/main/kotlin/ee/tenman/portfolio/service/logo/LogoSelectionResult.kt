package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.LogoSource

data class LogoSelectionResult(
  val selectedIndex: Int,
  val imageData: ByteArray,
  val source: LogoSource,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as LogoSelectionResult
    if (selectedIndex != other.selectedIndex) return false
    if (!imageData.contentEquals(other.imageData)) return false
    if (source != other.source) return false
    return true
  }

  override fun hashCode(): Int {
    var result = selectedIndex
    result = 31 * result + imageData.contentHashCode()
    result = 31 * result + source.hashCode()
    return result
  }
}
