package ee.tenman.portfolio.openfigi

import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class OpenFigiResolver(
  private val client: OpenFigiClient,
  @Value("\${openfigi.default-exchange-code:IM}") private val defaultExchangeCode: String,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val ITALY_SUFFIX = "IT"
  }

  fun resolveName(ticker: String): String? {
    val direct = lookup(ticker)
    if (direct != null) return direct
    val stripped = stripCountrySuffix(ticker) ?: return null
    log.debug("Retrying OpenFIGI with stripped ticker: ${LogSanitizerUtil.sanitize(stripped)} (from ${LogSanitizerUtil.sanitize(ticker)})")
    return lookup(stripped)
  }

  private fun lookup(ticker: String): String? =
    runCatching {
      val response = client.map(listOf(OpenFigiQuery(idType = "TICKER", idValue = ticker, exchCode = defaultExchangeCode)))
      response
        .firstOrNull()
        ?.data
        ?.firstOrNull()
        ?.name
    }.getOrElse {
      log.warn("OpenFIGI lookup failed for ticker ${LogSanitizerUtil.sanitize(ticker)}: ${it.message}")
      null
    }

  private fun stripCountrySuffix(ticker: String): String? =
    ticker
      .takeIf { it.endsWith(ITALY_SUFFIX) && it.length > ITALY_SUFFIX.length }
      ?.removeSuffix(ITALY_SUFFIX)
}
