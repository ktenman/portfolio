package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDate

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
  val transactionDate: LocalDate,
  @field:NotNull(message = "Platform is required")
  val platform: Platform,
  val commission: BigDecimal = BigDecimal.ZERO,
  @field:NotNull(message = "Currency is required")
  val currency: String = "EUR",
)

data class TransactionResponseDto(
  val id: Long?,
  val instrumentId: Long,
  val symbol: String,
  val name: String,
  val transactionType: TransactionType,
  val quantity: BigDecimal,
  val price: BigDecimal,
  val transactionDate: LocalDate,
  val platform: Platform,
  val realizedProfit: BigDecimal?,
  val unrealizedProfit: BigDecimal = BigDecimal.ZERO,
  val averageCost: BigDecimal?,
  val remainingQuantity: BigDecimal = BigDecimal.ZERO,
  val commission: BigDecimal = BigDecimal.ZERO,
  val currency: String = "EUR",
) {
  companion object {
    fun fromEntity(transaction: PortfolioTransaction) =
      TransactionResponseDto(
        id = transaction.id,
        instrumentId = transaction.instrument.id,
        symbol = transaction.instrument.symbol,
        name = transaction.instrument.name,
        transactionType = transaction.transactionType,
        quantity = transaction.quantity,
        price = transaction.price,
        transactionDate = transaction.transactionDate,
        platform = transaction.platform,
        realizedProfit = transaction.realizedProfit,
        unrealizedProfit = transaction.unrealizedProfit,
        averageCost = transaction.averageCost,
        remainingQuantity = transaction.remainingQuantity,
        commission = transaction.commission,
        currency = transaction.currency,
      )
  }
}

data class TransactionSummaryDto(
  val totalRealizedProfit: BigDecimal,
  val totalUnrealizedProfit: BigDecimal,
  val totalProfit: BigDecimal,
  val netInvested: BigDecimal,
)

data class TransactionsWithSummaryDto(
  val transactions: List<TransactionResponseDto>,
  val summary: TransactionSummaryDto,
)
