package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import jakarta.annotation.Resource
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@IntegrationTest
class CalculatorControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository

  @Test
  fun `should keep the current portfolio value out of the response`() {
    val instrument =
      instrumentRepository.save(
        Instrument(
          symbol = "QDVE:GER:EUR",
          name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
          category = "ETF",
          baseCurrency = "EUR",
          currentPrice = 28.25.toBigDecimal(),
        ),
      )
    portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = 3.4.toBigDecimal(),
        price = 27.25.toBigDecimal(),
        transactionDate = LocalDate.of(2023, 4, 1),
        platform = Platform.LIGHTYEAR,
      ),
    )

    mockMvc
      .perform(get("/api/calculator"))
      .andExpect(status().isOk)
      .andExpect(content().string(not(containsString("96.05"))))
  }
}
