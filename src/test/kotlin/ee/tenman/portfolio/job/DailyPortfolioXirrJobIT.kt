package ee.tenman.portfolio.job

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class DailyPortfolioXirrJobIT {
  @Autowired
  private lateinit var portfolioTransactionService: PortfolioTransactionService

  @Resource
  private lateinit var instrumentDataRetrievalJob: InstrumentDataRetrievalJob

  @Resource
  private lateinit var dailyPortfolioXirrJob: DailyPortfolioXirrJob

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var dailyPriceRepository: DailyPriceRepository

  @Resource
  private lateinit var portfolioDailySummaryRepository: PortfolioDailySummaryRepository

  @Test
  fun `should not create duplicated rows when triggered multiple times with same data`() {
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
        .withQueryParam("outputsize", equalTo("compact"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("alpha-vantage-response.json")
        )
    )

    instrumentDataRetrievalJob.retrieveInstrumentData()
    instrumentDataRetrievalJob.retrieveInstrumentData()

    dailyPortfolioXirrJob.calculateDailyPortfolioXirr()
    dailyPortfolioXirrJob.calculateDailyPortfolioXirr()

    assertThat(portfolioDailySummaryRepository.findAll()).hasSize(1).singleElement().satisfies({ it ->
      assertThat(it.entryDate).isEqualTo(LocalDate.now())
      assertThat(it.totalValue).isEqualByComparingTo(BigDecimal("101.8398000000"))
      assertThat(it.xirrAnnualReturn).isEqualByComparingTo(BigDecimal("4.27828650"))
      assertThat(it.totalProfit).isEqualByComparingTo(BigDecimal("1.8398000000"))
      assertThat(it.earningsPerDay).isEqualByComparingTo(BigDecimal("1.1929000000"))
    })
  }
}



