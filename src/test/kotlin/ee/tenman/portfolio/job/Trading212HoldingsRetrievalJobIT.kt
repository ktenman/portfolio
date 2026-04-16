package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.Trading212ScrapingProperties
import ee.tenman.portfolio.configuration.Trading212SymbolEntry
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.trading212.Trading212HoldingsService
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.wiremock.spring.InjectWireMock
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@IntegrationTest
@TestPropertySource(
  properties = [
    "cloudflare-bypass-proxy.url=http://localhost:\${wiremock.server.port}",
    "trading212.api.base-url=http://localhost:\${wiremock.server.port}",
    "trading212.api.key-id=test-id",
    "trading212.api.key-secret=test-secret",
    "openfigi.url=http://localhost:\${wiremock.server.port}",
  ],
)
class Trading212HoldingsRetrievalJobIT {
  @Resource
  private lateinit var jobTransactionService: JobTransactionService

  @Resource
  private lateinit var scrapingProperties: Trading212ScrapingProperties

  @Resource
  private lateinit var holdingsService: Trading212HoldingsService

  @Resource
  private lateinit var etfHoldingService: EtfHoldingService

  @Resource
  private lateinit var etfBreakdownService: EtfBreakdownService

  @Resource
  private lateinit var etfPositionRepository: EtfPositionRepository

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var clock: Clock

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  private lateinit var job: Trading212HoldingsRetrievalJob

  @BeforeEach
  fun setUp() {
    wireMockServer.resetAll()
    scrapingProperties.symbols.removeIf { it.symbol != "BNKE:PAR:EUR" }
    if (scrapingProperties.symbols.none { it.symbol == "BNKE:PAR:EUR" }) {
      scrapingProperties.symbols.add(Trading212SymbolEntry(symbol = "BNKE:PAR:EUR", ticker = "BNKEp_EQ"))
    }
    job =
      Trading212HoldingsRetrievalJob(
        jobTransactionService = jobTransactionService,
        scrapingProperties = scrapingProperties,
        holdingsService = holdingsService,
        etfHoldingService = etfHoldingService,
        etfBreakdownService = etfBreakdownService,
        instrumentRepository = instrumentRepository,
        clock = clock,
      )
    instrumentRepository.save(
      Instrument(
        symbol = "BNKE:PAR:EUR",
        name = "Amundi Euro Stoxx Banks UCITS ETF Acc",
        category = "ETF",
        baseCurrency = "EUR",
        providerName = ProviderName.TRADING212,
        providerExternalId = "BNKEp_EQ",
        currentPrice = BigDecimal.ZERO,
      ),
    )
  }

  @Test
  fun `should persist 29 etf positions for BNKE when full pipeline runs`() {
    stubHoldings()
    stubCatalogue()
    stubOpenFigi()

    job.runJob()

    val bnke = instrumentRepository.findBySymbol("BNKE:PAR:EUR").orElseThrow()
    val positions = etfPositionRepository.findLatestPositionsByEtfId(bnke.id)
    expect(positions.size).toEqual(29)
    val snapshotDates = positions.map { it.snapshotDate }.toSet()
    expect(snapshotDates).toContain(LocalDate.now(clock))
    val names = positions.map { it.holding.name }
    expect(names).toContain("Banco Santander")
    expect(names).toContain("Banca Monte Dei Paschi Siena Regr")
    expect(names).toContain("UNICREDIT SPA")
  }

  private fun stubHoldings() {
    wireMockServer.stubFor(
      get(urlPathEqualTo("/trading212/etf-holdings"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(readFixture("/fixtures/bnke-holdings.json")),
        ),
    )
  }

  private fun stubCatalogue() {
    wireMockServer.stubFor(
      get(urlPathEqualTo("/api/v0/equity/metadata/instruments"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(readFixture("/fixtures/t212-catalogue-subset.json")),
        ),
    )
  }

  private fun stubOpenFigi() {
    stubOpenFigiForTicker("UCG", "UNICREDIT SPA")
    stubOpenFigiForTicker("BPE", "BPER BANCA")
    stubOpenFigiForTicker("BAMI", "BANCO BPM SPA")
    stubOpenFigiForTicker("BGN", "BANCA GENERALI SPA")
    wireMockServer.stubFor(
      post(urlPathEqualTo("/v3/mapping"))
        .atPriority(10)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("[{\"data\":[]}]"),
        ),
    )
  }

  private fun stubOpenFigiForTicker(
    ticker: String,
    name: String,
  ) {
    wireMockServer.stubFor(
      post(urlPathEqualTo("/v3/mapping"))
        .atPriority(1)
        .withRequestBody(containing("\"idValue\":\"$ticker\""))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(openFigiMatchBody(ticker, name)),
        ),
    )
  }

  private fun readFixture(path: String): String = this::class.java.getResource(path)?.readText() ?: error("Fixture not found: $path")

  private fun openFigiMatchBody(
    ticker: String,
    name: String,
  ): String = """[{"data":[{"figi":"BBG000$ticker","name":"$name","ticker":"$ticker","exchCode":"IM","securityType":"Common Stock"}]}]"""
}
