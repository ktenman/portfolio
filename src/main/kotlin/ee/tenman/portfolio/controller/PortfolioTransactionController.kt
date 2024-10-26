package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.PortfolioTransactionService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
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
  fun createTransaction(@Valid @RequestBody request: TransactionRequestDto): TransactionResponseDto {
    val instrument = instrumentService.getInstrumentById(request.instrumentId)
    val transaction = PortfolioTransaction(
      instrument = instrument,
      transactionType = request.transactionType,
      quantity = request.quantity,
      price = request.price,
      transactionDate = request.transactionDate
    )
    val savedTransaction = portfolioTransactionService.saveTransaction(transaction)
    return TransactionResponseDto.fromEntity(savedTransaction)
  }

  @GetMapping
  @Loggable
  fun getAllTransactions(): List<TransactionResponseDto> {
    return portfolioTransactionService.getAllTransactions()
      .sortedByDescending { it.transactionDate }
      .map { TransactionResponseDto.fromEntity(it) }
  }

  @GetMapping("/{id}")
  @Loggable
  fun getTransaction(@PathVariable id: Long): TransactionResponseDto {
    val transaction = portfolioTransactionService.getTransactionById(id)
      ?: throw RuntimeException("Transaction not found with id: $id")
    return TransactionResponseDto.fromEntity(transaction)
  }

  @PutMapping("/{id}")
  @Loggable
  fun updateTransaction(
    @PathVariable id: Long,
    @Valid @RequestBody request: TransactionRequestDto
  ): TransactionResponseDto {
    val existingTransaction = portfolioTransactionService.getTransactionById(id)
      ?: throw RuntimeException("Transaction not found with id: $id")

    val instrument = instrumentService.getInstrumentById(request.instrumentId)

    existingTransaction.apply {
      this.instrument = instrument
      this.transactionType = request.transactionType
      this.quantity = request.quantity
      this.price = request.price
      this.transactionDate = request.transactionDate
    }

    val updatedTransaction = portfolioTransactionService.saveTransaction(existingTransaction)
    return TransactionResponseDto.fromEntity(updatedTransaction)
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun deleteTransaction(@PathVariable id: Long) = portfolioTransactionService.deleteTransaction(id)

  data class TransactionRequestDto(
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
  )

  // DTO for responses
  data class TransactionResponseDto(
    val id: Long?,
    val instrumentId: Long,
    val symbol: String,
    val transactionType: TransactionType,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val transactionDate: LocalDate,
    val currentValue: BigDecimal,
    val profit: BigDecimal
  ) {
    companion object {
      fun fromEntity(transaction: PortfolioTransaction) = TransactionResponseDto(
        id = transaction.id,
        instrumentId = transaction.instrument.id,
        symbol = transaction.instrument.symbol,
        transactionType = transaction.transactionType,
        quantity = transaction.quantity,
        price = transaction.price,
        transactionDate = transaction.transactionDate,
        currentValue = transaction.currentValue,
        profit = transaction.profit
      )
    }
  }

}
