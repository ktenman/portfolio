package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.cloudflarebypassproxy.CloudflareBypassProxyProperties
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class ImageSearchLogoService(
  private val restClient: RestClient,
  private val cloudflareBypassProxyProperties: CloudflareBypassProxyProperties,
  private val imageDownloadService: ImageDownloadService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun searchAndDownloadLogo(companyName: String): ImageSearchResult? {
    if (companyName.isBlank()) return null
    val urls = searchLogoUrls(companyName)
    val imageData = downloadFirstValidImage(urls) ?: return null
    return ImageSearchResult(imageData = imageData, source = LogoSource.BING)
  }

  fun searchLogoCandidates(
    companyName: String,
    maxResults: Int = 10,
  ): List<LogoCandidate> {
    if (companyName.isBlank()) return emptyList()
    log.debug("Searching Bing for company logo candidates: $companyName")
    val baseUrl = cloudflareBypassProxyProperties.url
    val request = ImageSearchRequest(query = "$companyName logo", maxResults = maxResults, squareOnly = true)
    val response =
      runCatching {
        restClient
          .post()
          .uri("$baseUrl$BING_ENDPOINT")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(ImageSearchResponse::class.java)
      }.onFailure { log.warn("Bing search request failed: ${it.message}") }.getOrNull()
    if (response == null || !response.success) return emptyList()
    val candidates =
      response.results.mapIndexed { index, result ->
        LogoCandidate(
          imageUrl = result.image,
          thumbnailUrl = result.thumbnail,
          title = result.title,
          index = index,
        )
      }
    log.debug("Bing returned ${candidates.size} logo candidates")
    return candidates
  }

  fun searchLogoUrls(
    companyName: String,
    maxResults: Int = 10,
  ): List<String> {
    if (companyName.isBlank()) return emptyList()
    log.debug("Searching Bing for company logo: $companyName")
    val baseUrl = cloudflareBypassProxyProperties.url
    val request = ImageSearchRequest(query = companyName, maxResults = maxResults, squareOnly = true)
    val response =
      runCatching {
        restClient
          .post()
          .uri("$baseUrl$BING_ENDPOINT")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(ImageSearchResponse::class.java)
      }.onFailure { log.warn("Bing search request failed: ${it.message}") }.getOrNull()
    if (response == null || !response.success) return emptyList()
    val urls = response.results.map { it.image }
    log.debug("Bing returned ${urls.size} image URLs")
    return urls
  }

  private fun downloadFirstValidImage(imageUrls: List<String>): ByteArray? {
    for (url in imageUrls) {
      val imageData =
        runCatching { imageDownloadService.download(url) }
          .onFailure { log.debug("Failed to download image from $url: ${it.message}") }
          .getOrNull()
      if (imageData != null && imageData.isNotEmpty()) {
        log.info("Successfully downloaded logo image from: $url")
        return imageData
      }
    }
    log.debug("No valid images found from ${imageUrls.size} URLs")
    return null
  }

  companion object {
    private const val BING_ENDPOINT = "/bing/images"
  }
}
