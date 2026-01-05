package ee.tenman.portfolio.service.integration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
      "Litecoin",
      "Ripple",
      "XRP",
      "Solana",
      "Cardano",
      "Dogecoin",
      "Polkadot",
      "Avalanche",
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
      "Xai Industrial Mar 26",
      "S&P 500 Jun 25",
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

  @ParameterizedTest
  @CsvSource(
    "Germany, DE",
    "germany, DE",
    "GERMANY, DE",
    "United States, US",
    "France, FR",
    "Japan, JP",
    "Luxembourg, LU",
  )
  fun `should extract country code from exact country name`(
    name: String,
    expectedCode: String,
  ) {
    expect(service.findCountryCodeByName(name)).toEqual(expectedCode)
  }

  @ParameterizedTest
  @CsvSource(
    "headquartered in Luxembourg, LU",
    "Spotify Technology SA is headquartered in Luxembourg, LU",
    "The company is based in Germany, DE",
    "located in France, FR",
    "Based in Japan, JP",
  )
  fun `should extract country code from verbose response`(
    response: String,
    expectedCode: String,
  ) {
    expect(service.findCountryCodeByName(response)).toEqual(expectedCode)
  }

  @ParameterizedTest
  @CsvSource(
    "United Arab Emirates, AE",
    "united arab emirates, AE",
    "South Africa, ZA",
    "New Zealand, NZ",
    "Saudi Arabia, SA",
    "South Korea, KR",
    "North Korea, KP",
    "Costa Rica, CR",
    "Puerto Rico, PR",
    "Sri Lanka, LK",
  )
  fun `should extract country code from multi-word country names`(
    name: String,
    expectedCode: String,
  ) {
    expect(service.findCountryCodeByName(name)).toEqual(expectedCode)
  }

  @ParameterizedTest
  @CsvSource(
    "headquartered in United Arab Emirates, AE",
    "The company is based in South Africa, ZA",
    "located in New Zealand, NZ",
    "Based in Saudi Arabia, SA",
  )
  fun `should extract multi-word country from verbose response`(
    response: String,
    expectedCode: String,
  ) {
    expect(service.findCountryCodeByName(response)).toEqual(expectedCode)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "Mali Corp",
      "Peru Holdings",
      "Chad Enterprises",
      "Some random text",
      "XX",
    ],
  )
  fun `should not extract country code from company names or invalid responses`(response: String) {
    expect(service.findCountryCodeByName(response)).toEqual(null)
  }

  @Test
  fun `should return empty map for empty batch`() {
    val result = service.classifyBatch(emptyList())
    expect(result).toEqual(emptyMap())
  }

  @Test
  fun `should auto-assign US for S&P 500 holdings in batch`() {
    val companies =
      listOf(
        CompanyClassificationInput(1L, "Apple Inc", "AAPL", listOf("S&P 500 ETF")),
        CompanyClassificationInput(2L, "Microsoft Corp", "MSFT", listOf("S&P 500 Index")),
      )
    val result = service.classifyBatch(companies)
    expect(result.keys).toHaveSize(2)
    expect(result[1L]?.countryCode).toEqual("US")
    expect(result[2L]?.countryCode).toEqual("US")
    expect(result[1L]?.model).toEqual(null)
    verify(exactly = 0) { openRouterClient.classifyWithCountryFallback(any()) }
  }

  @Test
  fun `should classify batch with LLM when not auto-assignable`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithCountryFallback(any()) } returns
      OpenRouterClassificationResult("1. DE\n2. FR", AiModel.GEMINI_3_FLASH_PREVIEW)
    val companies =
      listOf(
        CompanyClassificationInput(1L, "SAP SE", "SAP", emptyList()),
        CompanyClassificationInput(2L, "LVMH", "MC", emptyList()),
      )
    val result = service.classifyBatch(companies)
    expect(result.keys).toHaveSize(2)
    expect(result[1L]?.countryCode).toEqual("DE")
    expect(result[2L]?.countryCode).toEqual("FR")
    expect(result[1L]?.model).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }

  @Test
  fun `should skip non-company holdings in batch`() {
    val companies =
      listOf(
        CompanyClassificationInput(1L, "Bitcoin", null, emptyList()),
        CompanyClassificationInput(2L, "USD Cash", null, emptyList()),
      )
    val result = service.classifyBatch(companies)
    expect(result).toEqual(emptyMap())
    verify(exactly = 0) { openRouterClient.classifyWithCountryFallback(any()) }
  }

  @Test
  fun `should combine auto-assigned and LLM classified in batch`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithCountryFallback(any()) } returns
      OpenRouterClassificationResult("1. JP", AiModel.GEMINI_3_FLASH_PREVIEW)
    val companies =
      listOf(
        CompanyClassificationInput(1L, "Apple Inc", "AAPL", listOf("S&P 500 ETF")),
        CompanyClassificationInput(2L, "Toyota Motor", "TM", emptyList()),
      )
    val result = service.classifyBatch(companies)
    expect(result.keys).toHaveSize(2)
    expect(result[1L]?.countryCode).toEqual("US")
    expect(result[1L]?.model).toEqual(null)
    expect(result[2L]?.countryCode).toEqual("JP")
    expect(result[2L]?.model).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
  }
}
