package ee.tenman.portfolio.controller

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tenman.portfolio.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import jakarta.annotation.Resource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

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

  private fun setupInstrument(): Instrument {
    return instrumentRepository.save(
      Instrument(
        symbol = "QDVE",
        name = "iShares S&P 500 Information Technology Sector UCITS ETF USD (Acc)",
        category = "ETF",
        baseCurrency = "EUR"
      )
    )
  }

  @Test
  fun `should create a new portfolio transaction`() {
    val instrument = setupInstrument()
    val transactionDto = PortfolioTransactionController.PortfolioTransactionDto(
      id = null,
      instrumentId = instrument.id,
      transactionType = TransactionType.BUY,
      quantity = BigDecimal("10"),
      price = BigDecimal("100"),
      transactionDate = LocalDate.now()
    )

    mockMvc.perform(
      post("/api/transactions")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(transactionDto))
    )
      .andExpect(status().isCreated)
      .andExpect(jsonPath("$.id").isNotEmpty)
      .andExpect(jsonPath("$.instrumentId").value(instrument.id))
      .andExpect(jsonPath("$.transactionType").value("BUY"))
      .andExpect(jsonPath("$.quantity").value(10))
      .andExpect(jsonPath("$.price").value(100))
      .andExpect(jsonPath("$.transactionDate").value(LocalDate.now().toString()))

    val savedTransaction = portfolioTransactionRepository.findAll().first()
    assertThat(savedTransaction.instrument.id).isEqualTo(instrument.id)
    assertThat(savedTransaction.quantity).isEqualByComparingTo(BigDecimal("10"))
  }

  @Test
  fun `should return all transactions in the correct order`() {
    val instrument = setupInstrument()
    portfolioTransactionRepository.saveAll(
      listOf(
        PortfolioTransaction(
          instrument = instrument,
          transactionType = TransactionType.BUY,
          quantity = BigDecimal("10"),
          price = BigDecimal("100"),
          transactionDate = LocalDate.of(2023, 7, 18)
        ),
        PortfolioTransaction(
          instrument = instrument,
          transactionType = TransactionType.SELL,
          quantity = BigDecimal("5"),
          price = BigDecimal("150"),
          transactionDate = LocalDate.of(2023, 7, 19)
        )
      )
    )

    mockMvc.perform(get("/api/transactions"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$").isArray)
      .andExpect(jsonPath("$[0].transactionDate").value("2023-07-18"))
      .andExpect(jsonPath("$[1].transactionDate").value("2023-07-19"))

    val allTransactions = portfolioTransactionRepository.findAll().sortedBy { it.transactionDate }
    assertThat(allTransactions[0].transactionDate).isEqualTo(LocalDate.of(2023, 7, 18))
    assertThat(allTransactions[1].transactionDate).isEqualTo(LocalDate.of(2023, 7, 19))
  }

  @Test
  fun `should return a single transaction by ID`() {
    val instrument = setupInstrument()
    val transaction = portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = LocalDate.of(2023, 7, 18)
      )
    )

    mockMvc.perform(get("/api/transactions/${transaction.id}"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.id").value(transaction.id))
      .andExpect(jsonPath("$.transactionType").value("BUY"))
      .andExpect(jsonPath("$.quantity").value(10))
      .andExpect(jsonPath("$.price").value(100))
      .andExpect(jsonPath("$.transactionDate").value("2023-07-18"))

    val foundTransaction = portfolioTransactionRepository.findById(transaction.id)
    assertThat(foundTransaction).isPresent
  }

  @Test
  fun `should update an existing transaction`() {
    val instrument = setupInstrument()
    val transaction = portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = LocalDate.of(2023, 7, 18)
      )
    )

    val updatedDto = PortfolioTransactionController.PortfolioTransactionDto(
      id = transaction.id,
      instrumentId = instrument.id,
      transactionType = TransactionType.SELL,
      quantity = BigDecimal("5"),
      price = BigDecimal("150"),
      transactionDate = LocalDate.of(2023, 7, 19)
    )

    mockMvc.perform(
      put("/api/transactions/${transaction.id}")
        .contentType("application/json")
        .content(objectMapper.writeValueAsString(updatedDto))
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.id").value(transaction.id))
      .andExpect(jsonPath("$.transactionType").value("SELL"))
      .andExpect(jsonPath("$.quantity").value(5))
      .andExpect(jsonPath("$.price").value(150))
      .andExpect(jsonPath("$.transactionDate").value("2023-07-19"))

    val updatedTransaction = portfolioTransactionRepository.findById(transaction.id).get()
    assertThat(updatedTransaction.transactionType).isEqualTo(TransactionType.SELL)
    assertThat(updatedTransaction.quantity).isEqualByComparingTo(BigDecimal("5"))
  }

  @Test
  fun `should delete a transaction by ID`() {
    val instrument = setupInstrument()
    val transaction = portfolioTransactionRepository.save(
      PortfolioTransaction(
        instrument = instrument,
        transactionType = TransactionType.BUY,
        quantity = BigDecimal("10"),
        price = BigDecimal("100"),
        transactionDate = LocalDate.of(2023, 7, 18)
      )
    )

    mockMvc.perform(delete("/api/transactions/${transaction.id}"))
      .andExpect(status().isNoContent)

    assertThat(portfolioTransactionRepository.findById(transaction.id)).isEmpty
  }
}
