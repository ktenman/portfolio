package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.cloudflarebypassproxy.CloudflareBypassProxyProperties
import ee.tenman.portfolio.exception.LogoSearchUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

@Service
class ImageSearchLogoService(
  @param:Qualifier("logoSearchRestClient") private val restClient: RestClient,
  private val cloudflareBypassProxyProperties: CloudflareBypassProxyProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val baseUrl: String get() = cloudflareBypassProxyProperties.url.trimEnd('/')

  fun searchLogoCandidates(
    companyName: String,
    maxResults: Int = 10,
  ): List<LogoCandidate> {
    if (companyName.isBlank()) return emptyList()
    log.debug("Searching DuckDuckGo for company logo candidates: $companyName")
    val request = ImageSearchRequest(query = companyName, maxResults = maxResults)
    val response =
      runCatching {
        restClient
          .post()
          .uri("$baseUrl$DUCKDUCKGO_ENDPOINT")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(ImageSearchResponse::class.java)
      }.getOrElse { throw LogoSearchUnavailableException("DuckDuckGo search request failed for $companyName: ${it.message}", it) }
    if (response == null || !response.success) {
      throw LogoSearchUnavailableException("DuckDuckGo search failed for $companyName: ${response?.error}")
    }
    val candidates =
      response.results.mapIndexed { index, result ->
        LogoCandidate(
          imageUrl = proxyImageUrl(result.image),
          thumbnailUrl = proxyImageUrl(result.thumbnail),
          title = result.title,
          index = index,
        )
      }
    log.debug("DuckDuckGo returned ${candidates.size} logo candidates")
    return candidates
  }

  private fun proxyImageUrl(url: String): String {
    val encoded = URLEncoder.encode(url, UTF_8)
    return "$baseUrl/duckduckgo/image?url=$encoded"
  }

  companion object {
    private const val DUCKDUCKGO_ENDPOINT = "/duckduckgo/images"
  }
}
