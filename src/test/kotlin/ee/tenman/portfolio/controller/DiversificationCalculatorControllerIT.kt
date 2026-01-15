package ee.tenman.portfolio.controller

import ch.tutteli.atrium.api.fluent.en_GB.notToBeEmpty
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

private val DEFAULT_COOKIE = Cookie("AUTHSESSION", "test-session-id")

@IntegrationTest
class DiversificationCalculatorControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var cacheManager: CacheManager

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var etfHoldingRepository: EtfHoldingRepository

  @Resource
  private lateinit var etfPositionRepository: EtfPositionRepository

  @BeforeEach
  fun setup() {
    stubFor(
      WireMock
        .get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo("test-session-id"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("user-details-response.json"),
        ),
    )
    cacheManager.cacheNames.forEach { cacheName -> cacheManager.getCache(cacheName)?.clear() }
  }

  @Test
  fun `should return available etfs sorted by symbol`() {
    val etf1 = createAndSaveInstrument("VUAA", "Vanguard S&P 500")
    val etf2 = createAndSaveInstrument("VWCE", "Vanguard FTSE All-World")
    val etf3 = createAndSaveInstrument("CSPX", "iShares Core S&P 500")
    createEtfPositions(etf1)
    createEtfPositions(etf2)
    createEtfPositions(etf3)

    mockMvc
      .perform(get("/api/diversification/available-etfs").cookie(DEFAULT_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$").isArray)
      .andExpect(jsonPath("$.length()").value(3))
      .andExpect(jsonPath("$[0].symbol").value("CSPX"))
      .andExpect(jsonPath("$[1].symbol").value("VUAA"))
      .andExpect(jsonPath("$[2].symbol").value("VWCE"))

    val cached =
      mockMvc
        .perform(get("/api/diversification/available-etfs").cookie(DEFAULT_COOKIE))
        .andExpect(status().isOk)
        .andReturn()
    expect(cached.response.contentAsString).notToBeEmpty()
  }

  @Test
  fun `should return empty list when no etfs have positions`() {
    createAndSaveInstrument("VUAA", "Vanguard S&P 500")

    mockMvc
      .perform(get("/api/diversification/available-etfs").cookie(DEFAULT_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$").isArray)
      .andExpect(jsonPath("$.length()").value(0))
  }

  @Test
  fun `should calculate diversification for single etf`() {
    val etf = createAndSaveInstrument("VWCE", "Vanguard FTSE All-World", BigDecimal("0.22"))
    createEtfPositions(etf)

    mockMvc
      .perform(
        post("/api/diversification/calculate")
          .contentType(APPLICATION_JSON)
          .cookie(DEFAULT_COOKIE)
          .content(
            """
            {
              "allocations": [
                {"instrumentId": ${etf.id}, "percentage": 100}
              ]
            }
            """.trimIndent(),
          ),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.weightedTer").value(0.22))
      .andExpect(jsonPath("$.holdings").isArray)
      .andExpect(jsonPath("$.sectors").isArray)
      .andExpect(jsonPath("$.countries").isArray)
      .andExpect(jsonPath("$.concentration.top10Percentage").exists())
  }

  @Test
  fun `should calculate weighted ter for multiple etfs`() {
    val etf1 = createAndSaveInstrument("VWCE", "Vanguard FTSE All-World", BigDecimal("0.22"))
    val etf2 = createAndSaveInstrument("VUAA", "Vanguard S&P 500", BigDecimal("0.07"))
    createEtfPositions(etf1)
    createEtfPositions(etf2)

    mockMvc
      .perform(
        post("/api/diversification/calculate")
          .contentType(APPLICATION_JSON)
          .cookie(DEFAULT_COOKIE)
          .content(
            """
            {
              "allocations": [
                {"instrumentId": ${etf1.id}, "percentage": 50},
                {"instrumentId": ${etf2.id}, "percentage": 50}
              ]
            }
            """.trimIndent(),
          ),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.weightedTer").value(0.145))
  }

  @Test
  fun `should reject empty allocations`() {
    mockMvc
      .perform(
        post("/api/diversification/calculate")
          .contentType(APPLICATION_JSON)
          .cookie(DEFAULT_COOKIE)
          .content("""{"allocations": []}"""),
      ).andExpect(status().isBadRequest)
  }

  @Test
  fun `should reject negative instrument id`() {
    mockMvc
      .perform(
        post("/api/diversification/calculate")
          .contentType(APPLICATION_JSON)
          .cookie(DEFAULT_COOKIE)
          .content(
            """
            {
              "allocations": [
                {"instrumentId": -1, "percentage": 100}
              ]
            }
            """.trimIndent(),
          ),
      ).andExpect(status().isBadRequest)
  }

  @Test
  fun `should reject negative percentage`() {
    val etf = createAndSaveInstrument("VWCE", "Vanguard FTSE All-World")
    createEtfPositions(etf)

    mockMvc
      .perform(
        post("/api/diversification/calculate")
          .contentType(APPLICATION_JSON)
          .cookie(DEFAULT_COOKIE)
          .content(
            """
            {
              "allocations": [
                {"instrumentId": ${etf.id}, "percentage": -10}
              ]
            }
            """.trimIndent(),
          ),
      ).andExpect(status().isBadRequest)
  }

  @Test
  fun `should aggregate holdings from multiple etfs with same holding`() {
    val etf1 = createAndSaveInstrument("VWCE", "Vanguard FTSE All-World")
    val etf2 = createAndSaveInstrument("VUAA", "Vanguard S&P 500")
    val appleHolding = createAndSaveHolding("AAPL", "Apple Inc", "Technology", "US", "United States")
    createPosition(etf1, appleHolding, BigDecimal("5.0"))
    createPosition(etf2, appleHolding, BigDecimal("10.0"))

    mockMvc
      .perform(
        post("/api/diversification/calculate")
          .contentType(APPLICATION_JSON)
          .cookie(DEFAULT_COOKIE)
          .content(
            """
            {
              "allocations": [
                {"instrumentId": ${etf1.id}, "percentage": 50},
                {"instrumentId": ${etf2.id}, "percentage": 50}
              ]
            }
            """.trimIndent(),
          ),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.holdings.length()").value(1))
      .andExpect(jsonPath("$.holdings[0].name").value("Apple Inc"))
      .andExpect(jsonPath("$.holdings[0].percentage").value(7.5))
  }

  @Test
  fun `should aggregate sectors correctly`() {
    val etf = createAndSaveInstrument("VWCE", "Vanguard FTSE All-World")
    val holding1 = createAndSaveHolding("AAPL", "Apple Inc", "Technology", "US", "United States")
    val holding2 = createAndSaveHolding("MSFT", "Microsoft", "Technology", "US", "United States")
    val holding3 = createAndSaveHolding("JPM", "JPMorgan", "Financials", "US", "United States")
    createPosition(etf, holding1, BigDecimal("30.0"))
    createPosition(etf, holding2, BigDecimal("20.0"))
    createPosition(etf, holding3, BigDecimal("10.0"))

    mockMvc
      .perform(
        post("/api/diversification/calculate")
          .contentType(APPLICATION_JSON)
          .cookie(DEFAULT_COOKIE)
          .content(
            """
            {
              "allocations": [
                {"instrumentId": ${etf.id}, "percentage": 100}
              ]
            }
            """.trimIndent(),
          ),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.sectors.length()").value(2))
      .andExpect(jsonPath("$.sectors[0].sector").value("Technology"))
      .andExpect(jsonPath("$.sectors[0].percentage").value(50.0))
  }

  private fun createAndSaveInstrument(
    symbol: String,
    name: String,
    ter: BigDecimal? = BigDecimal("0.20"),
    annualReturn: BigDecimal? = BigDecimal("0.10"),
    currentPrice: BigDecimal? = BigDecimal("100.00"),
  ): Instrument {
    val instrument =
      Instrument(
        symbol = symbol,
        name = name,
        category = "ETF",
        baseCurrency = "EUR",
        providerName = ProviderName.LIGHTYEAR,
      ).apply {
        this.ter = ter
        this.xirrAnnualReturn = annualReturn
        this.currentPrice = currentPrice
      }
    return instrumentRepository.save(instrument)
  }

  private fun createAndSaveHolding(
    ticker: String?,
    name: String,
    sector: String?,
    countryCode: String?,
    countryName: String?,
  ): EtfHolding {
    val holding =
      EtfHolding(
        ticker = ticker,
        name = name,
        sector = sector,
        countryCode = countryCode,
        countryName = countryName,
      )
    return etfHoldingRepository.save(holding)
  }

  private fun createPosition(
    etf: Instrument,
    holding: EtfHolding,
    weight: BigDecimal,
  ): EtfPosition {
    val position =
      EtfPosition(
        etfInstrument = etf,
        holding = holding,
        weightPercentage = weight,
        snapshotDate = SNAPSHOT_DATE,
      )
    return etfPositionRepository.save(position)
  }

  private fun createEtfPositions(etf: Instrument) {
    val holding =
      createAndSaveHolding(
        "${etf.symbol}-HOLD",
        "${etf.name} Holding",
        "Technology",
        "US",
        "United States",
      )
    createPosition(etf, holding, BigDecimal("10.0"))
  }

  companion object {
    private val SNAPSHOT_DATE = LocalDate.of(2024, 1, 15)
  }
}
