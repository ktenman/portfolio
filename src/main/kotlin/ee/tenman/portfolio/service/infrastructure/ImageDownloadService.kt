package ee.tenman.portfolio.service.infrastructure

import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class ImageDownloadService(
  private val restClient: RestClient,
) {
  fun download(url: String): ByteArray =
    restClient
      .get()
      .uri(url)
      .retrieve()
      .body(ByteArray::class.java)
      ?: error("Empty response from $url")
}
