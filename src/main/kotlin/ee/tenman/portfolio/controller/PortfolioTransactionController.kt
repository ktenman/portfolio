package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.PortfolioTransactionService
import ee.tenman.portfolio.service.InstrumentService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/api/transactions")
@Validated
class PortfolioTransactionController(
  private val portfolioTransactionService: PortfolioTransactionService,
  private val instrumentService: InstrumentService
) {

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  fun createTransaction(@Valid @RequestBody transactionDto: PortfolioTransactionDto): PortfolioTransactionDto {
    val instrument = instrumentService.getInstrumentById(transactionDto.instrumentId)

    val transaction = PortfolioTransaction(
      instrument = instrument,
      transactionType = transactionDto.transactionType,
      quantity = transactionDto.quantity,
      price = transactionDto.price,
      transactionDate = transactionDto.transactionDate
    )

    val savedTransaction = portfolioTransactionService.saveTransaction(transaction)
    return PortfolioTransactionDto.fromEntity(savedTransaction)
  }

  @GetMapping
  @Loggable
  fun getAllTransactions(): List<PortfolioTransactionDto> {
    return portfolioTransactionService.getAllTransactions().map { PortfolioTransactionDto.fromEntity(it) }
  }

  @GetMapping("/{id}")
  @Loggable
  fun getTransaction(@PathVariable id: Long): PortfolioTransactionDto {
    val transaction = portfolioTransactionService.getTransactionById(id)
      ?: throw RuntimeException("Transaction not found with id: $id")
    return PortfolioTransactionDto.fromEntity(transaction)
  }

  @PutMapping("/{id}")
  @Loggable
  fun updateTransaction(@PathVariable id: Long, @Valid @RequestBody transactionDto: PortfolioTransactionDto): PortfolioTransactionDto {
    val existingTransaction = portfolioTransactionService.getTransactionById(id)
      ?: throw RuntimeException("Transaction not found with id: $id")

    val instrument = instrumentService.getInstrumentById(transactionDto.instrumentId)

    existingTransaction.apply {
      this.instrument = instrument
      this.transactionType = transactionDto.transactionType
      this.quantity = transactionDto.quantity
      this.price = transactionDto.price
      this.transactionDate = transactionDto.transactionDate
    }

    val updatedTransaction = portfolioTransactionService.saveTransaction(existingTransaction)
    return PortfolioTransactionDto.fromEntity(updatedTransaction)
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun deleteTransaction(@PathVariable id: Long) {
    portfolioTransactionService.deleteTransaction(id)
  }

  data class PortfolioTransactionDto(
    val id: Long?,
    @field:NotNull(message = "Instrument ID is required")
    val instrumentId: Long,
    @field:NotNull(message = "Transaction type is required")
    val transactionType: TransactionType,
    @field:NotNull(message = "Quantity is required")
    @field:Positive(message = "Quantity must be positive")
    val quantity: BigDecimal,
    @field:NotNull(message = "Price is required")
    @field:Positive(message = "Price must be positive")
    val price: BigDecimal,
    @field:NotNull(message = "Transaction date is required")
    val transactionDate: LocalDate
  ) {
    companion object {
      fun fromEntity(transaction: PortfolioTransaction) = PortfolioTransactionDto(
        id = transaction.id,
        instrumentId = transaction.instrument.id,
        transactionType = transaction.transactionType,
        quantity = transaction.quantity,
        price = transaction.price,
        transactionDate = transaction.transactionDate
      )
    }
  }
}
