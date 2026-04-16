package ee.tenman.portfolio.trading212

import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.openfigi.OpenFigiResolver
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class Trading212HoldingEnricher(
  private val catalogueService: Trading212CatalogueService,
  private val openFigiResolver: OpenFigiResolver,
) {
  companion object {
    private const val LOGO_URL_PREFIX = "https://trading212equities.s3.eu-central-1.amazonaws.com/"
    private val VALID_COUNTRY_CODES: Set<String> = Locale.getISOCountries().toSet()
  }

  fun enrich(
    holding: Trading212EtfHolding,
    rank: Int,
  ): HoldingData {
    val instrument = catalogueService.getInstrumentByTicker(holding.ticker)
    val countryCode = resolveCountryCode(instrument?.isin)
    return HoldingData(
      name = resolveName(holding, instrument),
      ticker = holding.ticker,
      sector = null,
      weight = holding.percentage,
      rank = rank,
      logoUrl = "$LOGO_URL_PREFIX${holding.ticker}.png",
      countryCode = countryCode,
      countryName = countryCode?.let(::resolveCountryName),
    )
  }

  private fun resolveName(
    holding: Trading212EtfHolding,
    instrument: Trading212Instrument?,
  ): String =
    instrument?.name?.takeIf { it.isNotBlank() }
      ?: holding.externalName?.takeIf { it.isNotBlank() }
      ?: openFigiResolver.resolveName(holding.ticker)?.takeIf { it.isNotBlank() }
      ?: holding.ticker

  private fun resolveCountryCode(isin: String?): String? =
    isin
      ?.take(2)
      ?.uppercase()
      ?.takeIf { it in VALID_COUNTRY_CODES }

  private fun resolveCountryName(code: String): String = Locale.of("", code).getDisplayCountry(Locale.ENGLISH)
}
