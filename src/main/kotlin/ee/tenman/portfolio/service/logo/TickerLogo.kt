package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.LogoSource

enum class TickerLogo(
  val source: LogoSource,
  private val template: String,
) {
  PARQET(LogoSource.PARQET, "https://assets.parqet.com/logos/symbol/%s?format=png&size=512"),
  NVSTLY(LogoSource.NVSTLY_ICONS, "https://raw.githubusercontent.com/nvstly/icons/main/ticker_icons/%s.png"),
  ;

  fun url(ticker: String): String = template.format(ticker.uppercase())
}
