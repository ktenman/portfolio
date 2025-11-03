package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.dto.TransactionRequestDto
import ee.tenman.portfolio.dto.TransactionResponseDto
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.TransactionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/transactions")
@Validated
class PortfolioTransactionController(
  private val portfolioTransactionService: TransactionService,
  private val instrumentService: InstrumentService,
) {
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  fun createTransaction(
    @Valid @RequestBody request: TransactionRequestDto,
  ): TransactionResponseDto {
    val instrument = instrumentService.getInstrumentById(request.instrumentId)
    val transaction =
      PortfolioTransaction(
        instrument = instrument,
        transactionType = request.transactionType,
        quantity = request.quantity,
        price = request.price,
        transactionDate = request.transactionDate,
        platform = request.platform,
        commission = request.commission,
        currency = request.currency,
      )
    val savedTransaction = portfolioTransactionService.saveTransaction(transaction)
    return TransactionResponseDto.fromEntity(savedTransaction)
  }

  @GetMapping
  @Loggable
  fun getAllTransactions(
    @RequestParam(required = false) platforms: List<String>?,
  ): List<TransactionResponseDto> {
    val transactions = portfolioTransactionService.getAllTransactions(platforms)
    portfolioTransactionService.calculateTransactionProfits(transactions)
    return transactions.map { TransactionResponseDto.fromEntity(it) }
  }

  @GetMapping("/{id}")
  @Loggable
  fun getTransaction(
    @PathVariable id: Long,
  ): TransactionResponseDto {
    val transaction = portfolioTransactionService.getTransactionById(id)
    portfolioTransactionService.calculateTransactionProfits(listOf(transaction))
    return TransactionResponseDto.fromEntity(transaction)
  }

  @PutMapping("/{id}")
  @Loggable
  fun updateTransaction(
    @PathVariable id: Long,
    @Valid @RequestBody request: TransactionRequestDto,
  ): TransactionResponseDto {
    val existingTransaction = portfolioTransactionService.getTransactionById(id)

    val instrument = instrumentService.getInstrumentById(request.instrumentId)

    existingTransaction.apply {
      this.instrument = instrument
      this.transactionType = request.transactionType
      this.quantity = request.quantity
      this.price = request.price
      this.transactionDate = request.transactionDate
      this.platform = request.platform
      this.commission = request.commission
      this.currency = request.currency
    }

    val updatedTransaction = portfolioTransactionService.saveTransaction(existingTransaction)
    return TransactionResponseDto.fromEntity(updatedTransaction)
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun deleteTransaction(
    @PathVariable id: Long,
  ) = portfolioTransactionService.deleteTransaction(id)
}
