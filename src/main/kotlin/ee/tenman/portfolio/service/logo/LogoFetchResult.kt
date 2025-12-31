package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.LogoSource

data class LogoFetchResult(
  val imageData: ByteArray,
  val source: LogoSource,
  val ticker: String? = null,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as LogoFetchResult
    if (!imageData.contentEquals(other.imageData)) return false
    if (source != other.source) return false
    if (ticker != other.ticker) return false
    return true
  }

  override fun hashCode(): Int {
    var result = imageData.contentHashCode()
    result = 31 * result + source.hashCode()
    result = 31 * result + (ticker?.hashCode() ?: 0)
    return result
  }
}
