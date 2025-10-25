package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import org.springframework.stereotype.Service

@Service
class LogoUrlService {
  fun generateLogoUrl(instrument: Instrument): String? {
    val ticker = instrument.symbol.uppercase()
    return generateLogoUrl(ticker)
  }

  fun generateLogoUrl(ticker: String): String? {
    val normalizedTicker = ticker.uppercase().split(":").first()
    return buildLightyearLogoUrl(normalizedTicker)
  }

  private fun buildLightyearLogoUrl(ticker: String): String =
    "https://assets.lightyear.com/logos/$ticker.png"

  fun generateFallbackLogoUrls(ticker: String): List<String> {
    val normalizedTicker = ticker.uppercase().split(":").first()

    return listOf(
      buildLightyearLogoUrl(normalizedTicker),
      buildYahooFinanceLogoUrl(normalizedTicker),
      buildAlternativeLightyearUrl(normalizedTicker),
    )
  }

  private fun buildYahooFinanceLogoUrl(ticker: String): String =
    "https://logo.yahoo.com/ticker/$ticker"

  private fun buildAlternativeLightyearUrl(ticker: String): String =
    "https://lightyear.com/assets/logos/$ticker.png"
}
