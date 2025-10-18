package ee.tenman.portfolio.dto

import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

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
