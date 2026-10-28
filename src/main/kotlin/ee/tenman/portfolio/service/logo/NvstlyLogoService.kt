package ee.tenman.portfolio.service.logo

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class NvstlyLogoService(
  private val restClient: RestClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun fetchLogo(ticker: String): ByteArray? = fetch(TICKER_ICONS, ticker)

  fun fetchCryptoLogo(symbol: String): ByteArray? = fetch(CRYPTO_ICONS, symbol)

  private fun fetch(
    folder: String,
    symbol: String,
  ): ByteArray? {
    if (symbol.isBlank()) return null
    val url = "$BASE_URL/$folder/${symbol.uppercase()}.png"
    log.debug("Fetching logo from nvstly/icons $folder for: $symbol")
    return runCatching {
      restClient
        .get()
        .uri(url)
        .retrieve()
        .body(ByteArray::class.java)
    }.onSuccess {
      log.info("Successfully fetched logo from nvstly/icons $folder for: $symbol")
    }.onFailure {
      log.debug("Logo not found in nvstly/icons $folder for: $symbol")
    }.getOrNull()
  }

  companion object {
    private const val BASE_URL = "https://raw.githubusercontent.com/nvstly/icons/main"
    private const val TICKER_ICONS = "ticker_icons"
    private const val CRYPTO_ICONS = "crypto_icons"
  }
}
