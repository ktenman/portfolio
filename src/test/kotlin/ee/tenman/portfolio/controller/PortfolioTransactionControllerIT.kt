package ee.tenman.portfolio.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.controller.PortfolioTransactionController.TransactionRequestDto
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import jakarta.annotation.Resource
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

private val DEFAULT_COOKIE = Cookie("AUTHSESSION", "NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz")

@ExtendWith(OutputCaptureExtension::class)
@IntegrationTest
class PortfolioTransactionControllerIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Resource
  private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository

  @Resource
  private lateinit var instrumentRepository: InstrumentRepository

  @Resource
  private lateinit var objectMapper: ObjectMapper

  private fun setupInstrument(): Instrument =
    instrumentRepository.save(
      Instrument(
        symbol = "QDVE",
        name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
        category = "ETF",
        baseCurrency = "EUR",
      ),
    )

  @BeforeEach
  fun setup() {
    stubFor(
      WireMock
        .get(urlPathEqualTo("/user-by-session"))
        .withQueryParam("sessionId", equalTo("NzEyYmI5ZTMtOTNkNy00MjQyLTgxYmItZWE4ZDA3OWI0N2Uz"))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBodyFile("user-details-response.json"),
        ),
    )
  }

  @Test
  fun `should create a new portfolio transaction`() {
    val instrument = setupInstrument()
    val transactionDto =
      TransactionRequestDto(
        id = null,
        instrumentId = instrument.id,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = LocalDate.now(),
        platform = Platform.TRADING212,
      )

    mockMvc
      .perform(
        post("/api/transactions")
          .contentType("application/json")
          .content(objectMapper.writeValueAsString(transactionDto))
          .cookie(DEFAULT_COOKIE),
      ).andExpect(status().isCreated)
      .andExpect(jsonPath("$.id").isNotEmpty)
      .andExpect(jsonPath("$.instrumentId").value(instrument.id))
      .andExpect(jsonPath("$.transactionType").value("BUY"))
      .andExpect(jsonPath("$.quantity").value(10))
      .andExpect(jsonPath("$.price").value(100))
      .andExpect(jsonPath("$.transactionDate").value(LocalDate.now().toString()))
      .andExpect(jsonPath("$.platform").value("TRADING212"))

    val savedTransaction = portfolioTransactionRepository.findAll().first()
    assertThat(savedTransaction.instrument.id).isEqualTo(instrument.id)
    assertThat(savedTransaction.quantity).isEqualByComparingTo(BigDecimal("10"))
  }

  @Test
  fun `should return all transactions in the correct order`() {
    val instrument =
      instrumentRepository.save(
        Instrument(
          symbol = "QDVE",
          name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
          category = "ETF",
          baseCurrency = "EUR",
          currentPrice = BigDecimal("29.62"),
        ),
      )

    val transaction1 =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("3.37609300"),
        price = BigDecimal("29.62"),
        transactionDate = LocalDate.of(2024, 7, 1),
        platform = Platform.SWEDBANK,
      )
    val transaction2 =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.SELL,
        quantity = BigDecimal("5"),
        price = BigDecimal("150"),
        transactionDate = LocalDate.of(2024, 7, 19),
        platform = Platform.SWEDBANK,
      )

    portfolioTransactionRepository.saveAll(listOf(transaction1, transaction2))

    mockMvc
      .perform(get("/api/transactions").cookie(DEFAULT_COOKIE))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$").isArray)
      .andExpect(jsonPath("$[0].transactionDate").value("2024-07-19"))
      .andExpect(jsonPath("$[0].transactionType").value("SELL"))
      .andExpect(jsonPath("$[0].quantity").value(5))
      .andExpect(jsonPath("$[0].price").value(150))
      .andExpect(jsonPath("$[0].realizedProfit").isNumber())
      .andExpect(jsonPath("$[0].averageCost").isNumber())
      .andExpect(jsonPath("$[1].transactionDate").value("2024-07-01"))
      .andExpect(jsonPath("$[1].transactionType").value("BUY"))
      .andExpect(jsonPath("$[1].quantity").value(3.37609300))
      .andExpect(jsonPath("$[1].price").value(29.62))
      .andExpect(jsonPath("$[1].unrealizedProfit").isNumber())
      .andExpect(jsonPath("$[1].averageCost").isNumber())
  }

  @Test
  fun `should return a single transaction by ID`() {
    val instrument = setupInstrument()
    val transaction =
      portfolioTransactionRepository.save(
        PortfolioTransaction(
          instrument = instrument,
          transactionType = TransactionType.BUY,
          quantity = BigDecimal("10"),
          price = BigDecimal("100"),
          transactionDate = LocalDate.of(2023, 7, 18),
          platform = Platform.LHV,
          unrealizedProfit = BigDecimal.ZERO,
          realizedProfit = BigDecimal.ZERO,
          averageCost = BigDecimal("100"),
        ),
      )

    mockMvc
      .perform(
        get("/api/transactions/${transaction.id}")
          .cookie(DEFAULT_COOKIE)
          .contentType(APPLICATION_JSON),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.id").value(transaction.id))
      .andExpect(jsonPath("$.instrumentId").value(instrument.id))
      .andExpect(jsonPath("$.symbol").value(instrument.symbol))
      .andExpect(jsonPath("$.transactionType").value(transaction.transactionType.name))
      .andExpect(jsonPath("$.quantity").value(10))
      .andExpect(jsonPath("$.price").value(100))
      .andExpect(jsonPath("$.transactionDate").value("2023-07-18"))
      .andExpect(jsonPath("$.platform").value(Platform.LHV.name))
      .andExpect(jsonPath("$.realizedProfit").value(0))
      .andExpect(jsonPath("$.unrealizedProfit").value(0))
      .andExpect(jsonPath("$.averageCost").value(100))

    assertThat(portfolioTransactionRepository.findAll().first())
      .matches { actualTransaction ->
        actualTransaction.id == transaction.id &&
          actualTransaction.instrument.id == instrument.id &&
          actualTransaction.transactionType == TransactionType.BUY &&
          actualTransaction.quantity.compareTo(BigDecimal("10")) == 0 &&
          actualTransaction.price.compareTo(BigDecimal("100")) == 0 &&
          actualTransaction.transactionDate == LocalDate.of(2023, 7, 18) &&
          actualTransaction.platform == Platform.LHV &&
          actualTransaction.unrealizedProfit.compareTo(BigDecimal.ZERO) == 0 &&
          actualTransaction.realizedProfit?.compareTo(BigDecimal.ZERO) == 0 &&
          actualTransaction.averageCost?.compareTo(BigDecimal("100")) == 0
      }
  }

  @Test
  fun `should update an existing transaction`() {
    val instrument = setupInstrument()
    val transaction =
      portfolioTransactionRepository.save(
        PortfolioTransaction(
          instrument = instrument,
          transactionType = TransactionType.BUY,
          quantity = BigDecimal("10"),
          price = BigDecimal("100"),
          transactionDate = LocalDate.of(2023, 7, 18),
          platform = Platform.LHV,
        ),
      )

    val updatedDto =
      TransactionRequestDto(
        id = transaction.id,
        instrumentId = instrument.id,
        transactionType = TransactionType.SELL,
        quantity = BigDecimal("5"),
        price = BigDecimal("150"),
        transactionDate = LocalDate.of(2023, 7, 19),
        platform = Platform.TRADING212,
      )

    mockMvc
      .perform(
        put("/api/transactions/${transaction.id}")
          .contentType("application/json")
          .content(objectMapper.writeValueAsString(updatedDto))
          .cookie(DEFAULT_COOKIE),
      ).andExpect(status().isOk)
      .andExpect(jsonPath("$.id").value(transaction.id))
      .andExpect(jsonPath("$.transactionType").value("SELL"))
      .andExpect(jsonPath("$.quantity").value(5))
      .andExpect(jsonPath("$.price").value(150))
      .andExpect(jsonPath("$.transactionDate").value("2023-07-19"))
      .andExpect(jsonPath("$.platform").value("TRADING212"))

    val updatedTransaction = portfolioTransactionRepository.findById(transaction.id).get()
    assertThat(updatedTransaction.transactionType).isEqualTo(TransactionType.SELL)
    assertThat(updatedTransaction.quantity).isEqualByComparingTo(BigDecimal("5"))
  }

  @Test
  fun `should delete a transaction by ID`() {
    val instrument = setupInstrument()
    val transaction =
      portfolioTransactionRepository.save(
        PortfolioTransaction(
          instrument = instrument,
          transactionType = TransactionType.BUY,
          quantity = BigDecimal("10"),
          price = BigDecimal("100"),
          transactionDate = LocalDate.of(2023, 7, 18),
          platform = Platform.LHV,
        ),
      )

    mockMvc
      .perform(delete("/api/transactions/${transaction.id}").cookie(DEFAULT_COOKIE))
      .andExpect(status().isNoContent)

    assertThat(portfolioTransactionRepository.findById(transaction.id)).isEmpty
  }
}
