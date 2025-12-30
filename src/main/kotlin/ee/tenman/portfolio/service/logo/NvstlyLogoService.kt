package ee.tenman.portfolio.service.logo

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class NvstlyLogoService(
  private val restClient: RestClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun fetchLogo(ticker: String): ByteArray? {
    if (ticker.isBlank()) return null
    val url = buildLogoUrl(ticker)
    log.debug("Fetching logo from nvstly/icons for ticker: $ticker")
    return runCatching {
      restClient
        .get()
        .uri(url)
        .retrieve()
        .body(ByteArray::class.java)
    }.onSuccess {
      log.info("Successfully fetched logo from nvstly/icons for ticker: $ticker")
    }.onFailure {
      log.debug("Logo not found in nvstly/icons for ticker: $ticker")
    }.getOrNull()
  }

  private fun buildLogoUrl(ticker: String): String = "$BASE_URL/${ticker.uppercase()}.png"

  companion object {
    private const val BASE_URL = "https://raw.githubusercontent.com/nvstly/icons/main/ticker_icons"
  }
}
