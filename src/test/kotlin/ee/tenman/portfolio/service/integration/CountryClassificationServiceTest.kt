package ee.tenman.portfolio.service.integration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CountryClassificationServiceTest {
  private val openRouterClient = mockk<OpenRouterClient>()
  private val properties = mockk<IndustryClassificationProperties>()

  private lateinit var service: CountryClassificationService

  @BeforeEach
  fun setUp() {
    service = CountryClassificationService(openRouterClient, properties)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "Other/cash",
      "other/cash",
      "Other/Cash",
      "othercash",
    ],
  )
  fun `should filter other cash holdings`(name: String) {
    expect(service.isNonCompanyHolding(name)).toEqual(true)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "Aud Cash",
      "AUD Cash",
      "Cad Cash",
      "Eur Cash",
      "Gbp Cash",
      "Ils Cash",
      "Jpy Cash",
      "Krw Cash",
      "Nok Cash",
      "Sek Cash",
      "Sgd Cash",
      "Usd Cash",
      "USD Cash",
      "CHF Cash",
      "HKD Cash",
      "NZD Cash",
      "TWD Cash",
      "DKK Cash",
      "PLN Cash",
      "CZK Cash",
      "HUF Cash",
      "INR Cash",
      "MXN Cash",
      "ZAR Cash",
    ],
  )
  fun `should filter currency cash positions`(name: String) {
    expect(service.isNonCompanyHolding(name)).toEqual(true)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "Cash Collateral Eur Msift",
      "Cash Collateral Usd Mlift",
      "Cash Collateral Usd Msift",
      "cash collateral something",
    ],
  )
  fun `should filter cash collateral positions`(name: String) {
    expect(service.isNonCompanyHolding(name)).toEqual(true)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "Australian Dollar",
      "Canadian Dollar",
      "Hong Kong Dollar",
      "New Zealand Dollar",
      "New Taiwan Dollar",
      "Singapore Dollar",
      "Us Dollar",
      "US Dollar",
    ],
  )
  fun `should filter dollar currencies`(name: String) {
    expect(service.isNonCompanyHolding(name)).toEqual(true)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "Danish Krone",
      "Japanese Yen",
      "Pound Sterling",
      "Swiss Franc",
      "Euro Currency",
    ],
  )
  fun `should filter other currencies`(name: String) {
    expect(service.isNonCompanyHolding(name)).toEqual(true)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "Bitcoin",
      "bitcoin",
      "BITCOIN",
      "Ethereum",
      "ethereum",
      "ETHEREUM",
    ],
  )
  fun `should filter crypto assets`(name: String) {
    expect(service.isNonCompanyHolding(name)).toEqual(true)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "Euro Stoxx 50 Mar 26",
      "Euro Stoxx 50 Dec 25",
    ],
  )
  fun `should filter index futures`(name: String) {
    expect(service.isNonCompanyHolding(name)).toEqual(true)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "Dollar General",
      "Dollar Tree",
      "Dollarama Inc",
      "Dollarama Inc.",
      "Coca-Cola Europacific Partners",
      "Eurobank Ergasias Services And Holdings Sa",
      "Eurofins Scientific",
      "Euronext",
      "Euronext Nv",
      "Franco-Nevada",
      "La Francaise Des Jeux Saem",
      "Keyence Corp",
      "Keyence Corporation",
      "Metcash Ltd",
      "Money Forward Inc",
      "Treasury Wine Estates Ltd",
      "Adyen",
      "Syensqo",
      "Neurocrine",
      "Apple Inc",
      "Microsoft Corporation",
      "Samsung Electronics",
    ],
  )
  fun `should not filter real companies`(name: String) {
    expect(service.isNonCompanyHolding(name)).toEqual(false)
  }
}
