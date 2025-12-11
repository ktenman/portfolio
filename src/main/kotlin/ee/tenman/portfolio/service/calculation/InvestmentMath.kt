package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.model.FinancialConstants.CALCULATION_SCALE
import java.math.BigDecimal
import java.math.RoundingMode

object InvestmentMath {
  fun calculateRealizedProfit(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions
      .filter { it.transactionType == TransactionType.SELL }
      .sumOf { it.realizedProfit ?: BigDecimal.ZERO }

  fun calculateTotalBuys(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions
      .filter { it.transactionType == TransactionType.BUY }
      .sumOf { it.price.multiply(it.quantity).add(it.commission) }

  fun calculateTotalSells(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions
      .filter { it.transactionType == TransactionType.SELL }
      .sumOf { it.price.multiply(it.quantity).subtract(it.commission) }

  fun calculateBuyQuantity(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions
      .filter { it.transactionType == TransactionType.BUY }
      .sumOf { it.quantity }

  fun calculateSellQuantity(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions
      .filter { it.transactionType == TransactionType.SELL }
      .sumOf { it.quantity }

  fun calculateRealizedGains(
    sellQuantity: BigDecimal,
    buyQuantity: BigDecimal,
    totalBuys: BigDecimal,
    totalSells: BigDecimal,
  ): BigDecimal {
    if (totalSells <= BigDecimal.ZERO || totalBuys <= BigDecimal.ZERO || buyQuantity <= BigDecimal.ZERO) {
      return BigDecimal.ZERO
    }
    val avgBuyPrice = totalBuys.divide(buyQuantity, CALCULATION_SCALE, RoundingMode.HALF_UP)
    return totalSells.subtract(avgBuyPrice.multiply(sellQuantity))
  }

  fun calculateUnrealizedGains(
    currentValue: BigDecimal,
    totalBuys: BigDecimal,
    soldCost: BigDecimal,
  ): BigDecimal = currentValue.subtract(totalBuys.subtract(soldCost))

  fun calculateSoldCost(
    transactions: List<PortfolioTransaction>,
    totalBuys: BigDecimal,
  ): BigDecimal {
    val buyQuantity = calculateBuyQuantity(transactions)
    if (buyQuantity <= BigDecimal.ZERO) return BigDecimal.ZERO
    val avgBuyPrice = totalBuys.divide(buyQuantity, CALCULATION_SCALE, RoundingMode.HALF_UP)
    return transactions
      .filter { it.transactionType == TransactionType.SELL }
      .sumOf { avgBuyPrice.multiply(it.quantity) }
  }

  fun calculateFallbackProfits(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
  ): Pair<BigDecimal, BigDecimal> {
    val totalBuys = calculateTotalBuys(transactions)
    val totalSells = calculateTotalSells(transactions)
    val buyQuantity = calculateBuyQuantity(transactions)
    val sellQuantity = calculateSellQuantity(transactions)
    val realizedGains = calculateRealizedGains(sellQuantity, buyQuantity, totalBuys, totalSells)
    val soldCost = calculateSoldCost(transactions, totalBuys)
    val unrealizedGains = calculateUnrealizedGains(currentValue, totalBuys, soldCost)
    return Pair(realizedGains, unrealizedGains)
  }
}
