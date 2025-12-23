package ee.tenman.portfolio.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "scraping.lightyear")
data class LightyearScrapingProperties(
  var etfs: List<EtfConfig> = emptyList(),
  var additionalInstruments: List<EtfConfig> = emptyList(),
) {
  data class EtfConfig(
    var symbol: String = "",
    var uuid: String = "",
  )

  fun getAllSymbols(): List<String> =
    (etfs + additionalInstruments)
      .map { it.symbol }
      .filter { it.isNotBlank() }

  fun getAllInstruments(): Map<String, String> =
    (etfs + additionalInstruments)
      .filter { it.uuid.isNotBlank() }
      .associate { it.symbol to it.uuid }

  fun findUuidBySymbol(symbol: String): String? {
    val allInstruments = getAllInstruments()
    allInstruments[symbol]?.let { return it }
    val matchingKey = allInstruments.keys.find { it.startsWith("$symbol:") }
    return matchingKey?.let { allInstruments[it] }
  }

  fun convertExchangeToLightyear(exchange: String): String = EXCHANGE_MAPPING[exchange.uppercase()] ?: exchange

  companion object {
    // Sync with: cloudflare-bypass-proxy/src/adapters/lightyear.ts EXCHANGE_MAPPING
    val EXCHANGE_MAPPING: Map<String, String> =
      mapOf(
      "GER" to "XETRA",
      "AEX" to "AMS",
      "MIL" to "MIL",
      "LON" to "LSE",
    )
  }
}
