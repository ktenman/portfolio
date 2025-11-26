package ee.tenman.portfolio.controller

import ee.tenman.portfolio.configuration.aspect.Loggable
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.dto.TransactionRequestDto
import ee.tenman.portfolio.dto.TransactionResponseDto
import ee.tenman.portfolio.dto.TransactionSummaryDto
import ee.tenman.portfolio.dto.TransactionsWithSummaryDto
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.InvestmentMetricsService
import ee.tenman.portfolio.service.TransactionService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
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
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/api/transactions")
@Validated
class PortfolioTransactionController(
  private val portfolioTransactionService: TransactionService,
  private val instrumentService: InstrumentService,
  private val investmentMetricsService: InvestmentMetricsService,
) {
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  fun createTransaction(
    @Valid @RequestBody request: TransactionRequestDto,
  ): TransactionResponseDto {
    val instrument = instrumentService.getInstrument(request.instrumentId)
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
    @RequestParam(required = false) platforms: String?,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: LocalDate?,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) untilDate: LocalDate?,
  ): TransactionsWithSummaryDto {
    val platformList = platforms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
    val transactions = portfolioTransactionService.getAllTransactions(platformList, fromDate, untilDate)

    val isDateFiltered = fromDate != null || untilDate != null
    if (isDateFiltered && transactions.isNotEmpty()) {
      val fullHistory =
        portfolioTransactionService.getTransactionHistory(transactions, platformList)
      portfolioTransactionService.calculateProfits(fullHistory)

      val profitMap = fullHistory.associateBy { it.id }
      transactions.forEach { tx ->
        profitMap[tx.id]?.let { calculated ->
          tx.realizedProfit = calculated.realizedProfit
          tx.unrealizedProfit = calculated.unrealizedProfit
          tx.remainingQuantity = calculated.remainingQuantity
          tx.averageCost = calculated.averageCost
        }
      }
    } else {
      portfolioTransactionService.calculateProfits(transactions)
    }

    val groupedByInstrument = transactions.groupBy { it.instrument }
    val totalUnrealizedProfit =
      groupedByInstrument.entries.sumOf { (instrument, instrumentTransactions) ->
        val metrics = investmentMetricsService.calculateInstrumentMetrics(instrument, instrumentTransactions)
        metrics.unrealizedProfit
      }

    val totalRealizedProfit =
      transactions
        .filter { it.transactionType == TransactionType.SELL }
        .sumOf { it.realizedProfit ?: BigDecimal.ZERO }

    val totalInvested =
      transactions.sumOf { transaction ->
        val transactionCost = transaction.price.multiply(transaction.quantity).add(transaction.commission)
        if (transaction.transactionType == TransactionType.BUY) {
          transactionCost
        } else {
          transactionCost.negate()
        }
      }

    val summary =
      TransactionSummaryDto(
        totalRealizedProfit = totalRealizedProfit,
        totalUnrealizedProfit = totalUnrealizedProfit,
        totalProfit = totalRealizedProfit + totalUnrealizedProfit,
        totalInvested = totalInvested,
      )

    return TransactionsWithSummaryDto(
      transactions = transactions.map { TransactionResponseDto.fromEntity(it) },
      summary = summary,
    )
  }

  @GetMapping("/{id}")
  @Loggable
  fun getTransaction(
    @PathVariable id: Long,
  ): TransactionResponseDto {
    val transaction = portfolioTransactionService.getTransaction(id)
    portfolioTransactionService.calculateProfits(listOf(transaction))
    return TransactionResponseDto.fromEntity(transaction)
  }

  @PutMapping("/{id}")
  @Loggable
  fun updateTransaction(
    @PathVariable id: Long,
    @Valid @RequestBody request: TransactionRequestDto,
  ): TransactionResponseDto {
    val existingTransaction = portfolioTransactionService.getTransaction(id)

    val instrument = instrumentService.getInstrument(request.instrumentId)

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
