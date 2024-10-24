package ee.tenman.portfolio.job

import com.github.tomakehurst.wiremock.client.WireMock.*
import ee.tenman.portfolio.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
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
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
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

  @MockBean
  private lateinit var clock: Clock

  @Test
  fun `should not create duplicated rows when triggered multiple times with same data`() {
    whenever(clock.instant()).thenReturn(Instant.parse("2024-07-05T00:00:00Z"))
    whenever(clock.zone).thenReturn(Clock.systemUTC().zone)
    val instrument = Instrument(
      "QDVE.DEX",
      "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
      "ETF",
      "EUR"
    ).let {
      instrumentRepository.save(it)
    }

    PortfolioTransaction(
      instrument = instrument,
      transactionType = TransactionType.BUY,
      quantity = BigDecimal("3.37609300"),
      price = BigDecimal("29.62003713"),
      transactionDate = LocalDate.of(2024, 7, 1)
    ).let {
      portfolioTransactionService.saveTransaction(it)
    }
    assertThat(dailyPriceRepository.findAll()).isEmpty()
    stubFor(
      get(urlPathEqualTo("/query"))
        .withQueryParam("function", equalTo("SYMBOL_SEARCH"))
        .withQueryParam("keywords", equalTo("QDVE.DEX"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("symbol-search-response.json")
        )
    )

    stubFor(
      get(urlPathEqualTo("/query"))
        .withQueryParam("function", equalTo("TIME_SERIES_DAILY"))
        .withQueryParam("symbol", equalTo("QDVE.DEX"))
        .withQueryParam("outputsize", equalTo("full"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("alpha-vantage-response.json")
        )
    )

    alphaVantageDataRetrievalJob.execute()
    alphaVantageDataRetrievalJob.execute()

    dailyPortfolioXirrJob.execute()
    dailyPortfolioXirrJob.execute()

    val summaries = portfolioDailySummaryRepository.findAll()
    assertThat(summaries).hasSize(4)
    summaries.minByOrNull { it.entryDate }!!.let {
      assertThat(it.entryDate).isEqualTo("2024-07-01")
      assertThat(it.totalValue).isEqualByComparingTo(BigDecimal("100.4556000000"))
      assertThat(it.xirrAnnualReturn).isEqualByComparingTo(BigDecimal("0.00455647"))
      assertThat(it.totalProfit).isEqualByComparingTo(BigDecimal("0.4556000000"))
      assertThat(it.earningsPerDay).isEqualByComparingTo(BigDecimal("0.0013000000"))
    }
  }
}



