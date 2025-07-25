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
import ee.tenman.portfolio.service.PortfolioTransactionService
import jakarta.annotation.Resource
import org.assertj.core.api.Assertions.assertThat
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
  private lateinit var portfolioTransactionService: PortfolioTransactionService

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
      portfolioTransactionService.saveTransaction(it)
    }
    assertThat(dailyPriceRepository.findAll()).isEmpty()
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
    assertThat(summaries).hasSize(4)
    summaries.minByOrNull { it.entryDate }!!.let {
      assertThat(it.entryDate).isEqualTo("2024-07-01")
      assertThat(it.totalValue).isEqualByComparingTo(BigDecimal("100.4556472150"))
      assertThat(it.xirrAnnualReturn).isEqualByComparingTo(BigDecimal("0E-10"))
      assertThat(it.totalProfit).isEqualByComparingTo(BigDecimal("0.4556472007"))
      assertThat(it.earningsPerDay).isEqualByComparingTo(BigDecimal("0E-10"))
    }
  }
}
