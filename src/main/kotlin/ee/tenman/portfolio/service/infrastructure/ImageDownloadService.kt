package ee.tenman.portfolio.service.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URI

@Service
class ImageDownloadService(
  private val restClient: RestClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun downloadOrNull(url: String): ByteArray? =
    runCatching { download(url) }
      .onFailure { log.debug("Failed to download image from $url: ${it.message}") }
      .getOrNull()

  fun download(url: String): ByteArray {
    validateUrl(url)
    val data =
      restClient
      .get()
      .uri(url)
      .retrieve()
      .body(ByteArray::class.java)
      ?: error("Empty response from $url")
    if (data.size > MAX_DOWNLOAD_SIZE_BYTES) {
      error("Image too large: ${data.size} bytes exceeds limit of $MAX_DOWNLOAD_SIZE_BYTES")
    }
    return data
  }

  private fun validateUrl(url: String) {
    val uri = runCatching { URI(url) }.getOrElse { error("Invalid URL: $url") }
    val scheme = uri.scheme?.lowercase()
    if (scheme !in ALLOWED_SCHEMES) {
      error("Invalid URL scheme: $scheme (allowed: $ALLOWED_SCHEMES)")
    }
  }

  companion object {
    private val ALLOWED_SCHEMES = setOf("http", "https")
    private const val MAX_DOWNLOAD_SIZE_BYTES = 2 * 1024 * 1024
  }
}
