package ee.tenman.portfolio.job

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioDailySummaryRepository
import ee.tenman.portfolio.service.TransactionService
import jakarta.annotation.Resource
import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@IntegrationTest
class DailyPortfolioXirrJobIT {
  @Resource
  private lateinit var transactionService: TransactionService

  @Resource
  private lateinit var alphaVantageDataRetrievalJob: AlphaVantageDataRetrievalJob

  @Resource
  private lateinit var dailyPortfolioXirrJob: DailyPortfolioXirrJob

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var dailyPriceRepository: DailyPriceRepository

  @Resource
  private lateinit var portfolioDailySummaryRepository: PortfolioDailySummaryRepository

  @MockitoBean
  private lateinit var clock: Clock

  @Test
  fun `should not create duplicated rows when triggered multiple times with same data`() {
    whenever(clock.instant()).thenReturn(Instant.parse("2024-07-05T00:00:00Z"))
    whenever(clock.zone).thenReturn(Clock.systemUTC().zone)
    val instrument =
      Instrument(
        "QDVE.DEX",
        "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
        "ETF",
        "EUR",
      ).let {
        instrumentRepository.save(it)
      }

    PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = BigDecimal("3.37609300"),
      price = BigDecimal("29.62003713"),
      transactionDate = LocalDate.of(2024, 7, 1),
      platform = Platform.SWEDBANK,
    ).let {
      transactionService.saveTransaction(it)
    }
    expect(dailyPriceRepository.findAll()).toBeEmpty()
    stubFor(
      get(urlPathEqualTo("/query"))
        .withQueryParam("function", equalTo("SYMBOL_SEARCH"))
        .withQueryParam("keywords", matching("QDVE.*"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("symbol-search-response.json"),
        ),
    )

    stubFor(
      get(urlPathEqualTo("/query"))
        .withQueryParam("function", equalTo("TIME_SERIES_DAILY"))
        .withQueryParam("symbol", equalTo("QDVE.DEX"))
        .withQueryParam("outputsize", equalTo("full"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("alpha-vantage-response.json"),
        ),
    )

    alphaVantageDataRetrievalJob.execute()
    alphaVantageDataRetrievalJob.execute()

    dailyPortfolioXirrJob.execute()
    dailyPortfolioXirrJob.execute()

    val summaries = portfolioDailySummaryRepository.findAll()
    expect(summaries).toHaveSize(4)
    val minSummary = summaries.minByOrNull { it.entryDate }!!
    expect(minSummary.entryDate.toString()).toEqual("2024-07-01")
    expect(minSummary.totalValue.compareTo(BigDecimal("100.4556472150"))).toEqual(0)
    expect(minSummary.xirrAnnualReturn.compareTo(BigDecimal("0E-10"))).toEqual(0)
    expect(minSummary.totalProfit.compareTo(BigDecimal("0.4556472007"))).toEqual(0)
    expect(minSummary.earningsPerDay.compareTo(BigDecimal("0E-10"))).toEqual(0)
  }
}
