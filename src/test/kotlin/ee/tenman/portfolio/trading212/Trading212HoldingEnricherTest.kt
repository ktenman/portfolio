package ee.tenman.portfolio.trading212

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.openfigi.OpenFigiResolver
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class Trading212HoldingEnricherTest {
  private val catalogueService = mockk<Trading212CatalogueService>()
  private val openFigiResolver = mockk<OpenFigiResolver>()
  private val enricher = Trading212HoldingEnricher(catalogueService, openFigiResolver)

  @Test
  fun `should enrich from catalogue when ticker is known`() {
    every { catalogueService.getInstrumentByTicker("SANe_EQ") } returns
      Trading212Instrument(
        ticker = "SANe_EQ",
        type = "STOCK",
        isin = "ES0113900J37",
        currencyCode = "EUR",
        name = "Banco Santander",
        shortName = "SAN",
      )
    val result = enricher.enrich(Trading212EtfHolding(ticker = "SANe_EQ", percentage = BigDecimal("13.94"), externalName = null), rank = 1)
    expect(result.name).toEqual("Banco Santander")
    expect(result.ticker).toEqual("SANe_EQ")
    expect(result.weight).toEqualNumerically(BigDecimal("13.94"))
    expect(result.rank).toEqual(1)
    expect(result.logoUrl).toEqual("https://trading212equities.s3.eu-central-1.amazonaws.com/SANe_EQ.png")
    expect(result.countryCode).toEqual("ES")
    expect(result.countryName).toEqual("Spain")
    verify(exactly = 0) { openFigiResolver.resolveName(any()) }
  }

  @Test
  fun `should resolve country from Italian ISIN`() {
    every { catalogueService.getInstrumentByTicker("IESd_EQ") } returns
      Trading212Instrument(
        ticker = "IESd_EQ",
        type = "STOCK",
        isin = "IT0000072618",
        currencyCode = "EUR",
        name = "Intesa Sanpaolo",
        shortName = "IES",
      )
    val result = enricher.enrich(Trading212EtfHolding(ticker = "IESd_EQ", percentage = BigDecimal("7.89"), externalName = null), rank = 5)
    expect(result.countryCode).toEqual("IT")
    expect(result.countryName).toEqual("Italy")
  }

  @Test
  fun `should resolve country from Dutch ISIN`() {
    every { catalogueService.getInstrumentByTicker("INGAa_EQ") } returns
      Trading212Instrument(
        ticker = "INGAa_EQ",
        type = "STOCK",
        isin = "NL0011821202",
        currencyCode = "EUR",
        name = "ING Groep",
        shortName = "INGA",
      )
    val result = enricher.enrich(Trading212EtfHolding(ticker = "INGAa_EQ", percentage = BigDecimal("6.45"), externalName = null), rank = 6)
    expect(result.countryCode).toEqual("NL")
    expect(result.countryName).toEqual("Netherlands")
  }

  @Test
  fun `cannot resolve country when catalogue entry is missing`() {
    every { catalogueService.getInstrumentByTicker("BMPS") } returns null
    val result =
      enricher.enrich(
        Trading212EtfHolding(ticker = "BMPS", percentage = BigDecimal("1.63"), externalName = "Banca Monte Dei Paschi Siena Regr"),
        rank = 18,
      )
    expect(result.countryCode).toEqual(null)
    expect(result.countryName).toEqual(null)
  }

  @Test
  fun `cannot resolve country when ISIN is blank or invalid`() {
    every { catalogueService.getInstrumentByTicker("NOISIN_EQ") } returns
      Trading212Instrument(
        ticker = "NOISIN_EQ",
        type = "STOCK",
        isin = null,
        currencyCode = "EUR",
        name = "NoIsin",
        shortName = "NI",
      )
    every { catalogueService.getInstrumentByTicker("BADISIN_EQ") } returns
      Trading212Instrument(
        ticker = "BADISIN_EQ",
        type = "STOCK",
        isin = "XS1234567890",
        currencyCode = "EUR",
        name = "Supranational",
        shortName = "XS",
      )
    val noIsin =
      enricher.enrich(
        Trading212EtfHolding(ticker = "NOISIN_EQ", percentage = BigDecimal("0.5"), externalName = null),
        rank = 30,
      )
    val badIsin =
      enricher.enrich(
        Trading212EtfHolding(ticker = "BADISIN_EQ", percentage = BigDecimal("0.5"), externalName = null),
        rank = 31,
      )
    expect(noIsin.countryCode).toEqual(null)
    expect(badIsin.countryCode).toEqual(null)
  }

  @Test
  fun `should enrich from externalName when catalogue miss and externalName present`() {
    every { catalogueService.getInstrumentByTicker("BMPS") } returns null
    val result =
      enricher.enrich(
      Trading212EtfHolding(ticker = "BMPS", percentage = BigDecimal("1.63"), externalName = "Banca Monte Dei Paschi Siena Regr"),
      rank = 18,
    )
    expect(result.name).toEqual("Banca Monte Dei Paschi Siena Regr")
    verify(exactly = 0) { openFigiResolver.resolveName(any()) }
  }

  @Test
  fun `should enrich from OpenFIGI when catalogue and externalName miss`() {
    every { catalogueService.getInstrumentByTicker("UCG") } returns null
    every { openFigiResolver.resolveName("UCG") } returns "UNICREDIT SPA"
    val result = enricher.enrich(Trading212EtfHolding(ticker = "UCG", percentage = BigDecimal("9.18"), externalName = null), rank = 3)
    expect(result.name).toEqual("UNICREDIT SPA")
  }

  @Test
  fun `cannot find name anywhere and falls back to raw ticker`() {
    every { catalogueService.getInstrumentByTicker("XYZ") } returns null
    every { openFigiResolver.resolveName("XYZ") } returns null
    val result = enricher.enrich(Trading212EtfHolding(ticker = "XYZ", percentage = BigDecimal("0.10"), externalName = null), rank = 30)
    expect(result.name).toEqual("XYZ")
  }
}
