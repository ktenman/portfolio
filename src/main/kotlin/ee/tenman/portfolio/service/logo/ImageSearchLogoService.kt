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
    return tryBingSearch(companyName) ?: tryDuckDuckGoSearch(companyName)
  }

  fun searchLogoUrls(
    companyName: String,
    maxResults: Int = 5,
  ): List<String> {
    if (companyName.isBlank()) return emptyList()
    val bingResults = searchWithBing(companyName, maxResults)
    if (bingResults.isNotEmpty()) return bingResults
    log.debug("Bing returned no results, falling back to DuckDuckGo")
    return searchWithDuckDuckGo(companyName, maxResults)
  }

  private fun tryBingSearch(companyName: String): ImageSearchResult? {
    val urls = searchWithBing(companyName, DEFAULT_MAX_RESULTS)
    val imageData = downloadFirstValidImage(urls) ?: return null
    return ImageSearchResult(imageData = imageData, source = LogoSource.BING)
  }

  private fun tryDuckDuckGoSearch(companyName: String): ImageSearchResult? {
    log.debug("Bing returned no results, falling back to DuckDuckGo")
    val urls = searchWithDuckDuckGo(companyName, DEFAULT_MAX_RESULTS)
    val imageData = downloadFirstValidImage(urls) ?: return null
    return ImageSearchResult(imageData = imageData, source = LogoSource.DUCKDUCKGO)
  }

  private fun searchWithBing(
    companyName: String,
    maxResults: Int,
  ): List<String> {
    log.debug("Searching Bing for company logo: $companyName")
    val response = executeImageSearch("$BING_ENDPOINT", companyName, maxResults)
    return extractImageUrls(response, "Bing")
  }

  private fun searchWithDuckDuckGo(
    companyName: String,
    maxResults: Int,
  ): List<String> {
    log.debug("Searching DuckDuckGo for company logo: $companyName")
    val response = executeImageSearch("$DUCKDUCKGO_ENDPOINT", companyName, maxResults)
    return extractImageUrls(response, "DuckDuckGo")
  }

  private fun executeImageSearch(
    endpoint: String,
    companyName: String,
    maxResults: Int,
  ): ImageSearchResponse? {
    val baseUrl = cloudflareBypassProxyProperties.url
    val request = ImageSearchRequest(query = companyName, maxResults = maxResults, squareOnly = true)
    return runCatching {
      restClient
        .post()
        .uri("$baseUrl$endpoint")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(ImageSearchResponse::class.java)
    }.onFailure {
      log.warn("Image search request failed: ${it.message}")
    }.getOrNull()
  }

  private fun extractImageUrls(
    response: ImageSearchResponse?,
    source: String,
  ): List<String> {
    if (response == null || !response.success) return emptyList()
    val urls = response.results.map { it.image }
    log.debug("$source returned ${urls.size} image URLs")
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
    private const val DUCKDUCKGO_ENDPOINT = "/duckduckgo/images"
    private const val DEFAULT_MAX_RESULTS = 5
  }
}
