package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Service
class InvestmentMetricsService(
  private val dailyPriceService: DailyPriceService,
  private val transactionService: TransactionService,
) {
  private companion object {
    private val log = LoggerFactory.getLogger(InvestmentMetricsService::class.java)
  }

  data class InstrumentMetrics(
    val totalInvestment: BigDecimal,
    val currentValue: BigDecimal,
    val profit: BigDecimal,
    val xirr: Double,
    val quantity: BigDecimal,
  ) {
    override fun toString(): String =
      "InstrumentMetrics(totalInvestment=$totalInvestment, " +
        "currentValue=$currentValue, " +
        "profit=$profit, " +
        "xirr=${String.format("%.2f%%", xirr * 100)})"

    companion object {
      val EMPTY =
        InstrumentMetrics(
          totalInvestment = BigDecimal.ZERO,
          currentValue = BigDecimal.ZERO,
          profit = BigDecimal.ZERO,
          xirr = 0.0,
          quantity = BigDecimal.ZERO,
        )
    }
  }

  data class PortfolioMetrics(
    var totalValue: BigDecimal = BigDecimal.ZERO,
    var totalProfit: BigDecimal = BigDecimal.ZERO,
    val xirrTransactions: MutableList<Transaction> = mutableListOf(),
  )

  fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, BigDecimal> {
    var quantity = BigDecimal.ZERO
    var totalCost = BigDecimal.ZERO

    transactions.sortedBy { it.transactionDate }.forEach { transaction ->
      when (transaction.transactionType) {
        TransactionType.BUY -> {
          val cost = transaction.price.multiply(transaction.quantity).add(transaction.commission)
          totalCost = totalCost.add(cost)
          quantity = quantity.add(transaction.quantity)
        }

        TransactionType.SELL -> {
          if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            val sellRatio = transaction.quantity.divide(quantity, 10, RoundingMode.HALF_UP)
            totalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio))
            quantity = quantity.subtract(transaction.quantity)
          }
        }
      }
    }

    val averageCost =
      if (quantity.compareTo(BigDecimal.ZERO) > 0) {
        totalCost.divide(quantity, 10, RoundingMode.HALF_UP)
      } else {
        BigDecimal.ZERO
      }

    return Pair(quantity, averageCost)
  }

  fun calculateCurrentValue(
    holdings: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal = holdings.multiply(currentPrice)

  fun calculateProfit(
    holdings: BigDecimal,
    averageCost: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal {
    val currentValue = holdings.multiply(currentPrice)
    val investment = holdings.multiply(averageCost)
    return currentValue.subtract(investment)
  }

  fun buildXirrTransactions(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    calculationDate: LocalDate = LocalDate.now(),
  ): List<Transaction> {
    val cashflows = transactions.map { convertToXirrTransaction(it) }

    return if (currentValue > BigDecimal.ZERO) {
      cashflows + Transaction(currentValue.toDouble(), calculationDate)
    } else {
      cashflows
    }
  }

  fun calculateAdjustedXirr(
    transactions: List<Transaction>,
    calculationDate: LocalDate = LocalDate.now(),
  ): Double {
    if (transactions.size < 2) return 0.0

    return try {
      val xirrResult = Xirr(transactions).calculate()
      val cashFlows = transactions.filter { it.amount < 0 }

      if (cashFlows.isEmpty()) {
        0.0
      } else {
        val weightedDays = calculateWeightedInvestmentAge(cashFlows, calculationDate)
        val dampingFactor = min(1.0, weightedDays / 60.0)
        val boundedXirr = xirrResult.coerceIn(-10.0, 10.0)
        boundedXirr * dampingFactor
      }
    } catch (e: Exception) {
      log.error("Error calculating adjusted XIRR", e)
      0.0
    }
  }

  private fun calculateWeightedInvestmentAge(
    cashFlows: List<Transaction>,
    calculationDate: LocalDate,
  ): Double {
    val totalInvestment = cashFlows.sumOf { -it.amount }
    return cashFlows.sumOf { transaction ->
      val weight = -transaction.amount / totalInvestment
      val days = ChronoUnit.DAYS.between(transaction.date, calculationDate).toDouble()
      days * weight
    }
  }

  fun calculateInstrumentMetrics(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    calculationDate: LocalDate = LocalDate.now(),
  ): InstrumentMetrics {
    if (transactions.isEmpty()) {
      return InstrumentMetrics.EMPTY
    }

    val groupedByPlatform = transactions.groupBy { it.platform }

    var totalInvestment = BigDecimal.ZERO
    var totalHoldings = BigDecimal.ZERO

    groupedByPlatform.forEach { (_, platformTransactions) ->
      val (quantity, averageCost) = calculateCurrentHoldings(platformTransactions)
      if (quantity > BigDecimal.ZERO) {
        val investment = quantity.multiply(averageCost)
        totalInvestment = totalInvestment.add(investment)
        totalHoldings = totalHoldings.add(quantity)
      }
    }

    val currentPrice = instrument.currentPrice ?: BigDecimal.ZERO
    val currentValue = calculateCurrentValue(totalHoldings, currentPrice)
    val profit = currentValue.subtract(totalInvestment)
    val xirrTransactions = buildXirrTransactions(transactions, currentValue, calculationDate)
    val xirr = calculateAdjustedXirr(xirrTransactions, calculationDate)

    return InstrumentMetrics(
      totalInvestment = totalInvestment,
      currentValue = currentValue,
      profit = profit,
      xirr = xirr,
      quantity = totalHoldings,
    )
  }

  fun calculateInstrumentMetricsWithProfits(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    calculationDate: LocalDate = LocalDate.now(),
  ): InstrumentMetrics {
    if (transactions.isEmpty()) {
      return InstrumentMetrics.EMPTY
    }

    transactionService.calculateTransactionProfits(transactions)
    return calculateInstrumentMetrics(instrument, transactions, calculationDate)
  }

  fun calculatePortfolioMetrics(
    instrumentGroups: Map<Instrument, List<PortfolioTransaction>>,
    date: LocalDate,
  ): PortfolioMetrics {
    val metrics = PortfolioMetrics()

    val allTransactions = instrumentGroups.values.flatten()
    transactionService.calculateTransactionProfits(allTransactions)

    instrumentGroups.forEach { (instrument, instrumentTransactions) ->
      try {
        processInstrumentWithUnifiedCalculation(instrument, instrumentTransactions, date, metrics)
      } catch (e: Exception) {
        log.warn("Unified calculation failed, using fallback: ${e.message}")
        processInstrumentWithFallback(instrument, instrumentTransactions, date, metrics)
      }
    }

    return metrics
  }

  private fun processInstrumentWithUnifiedCalculation(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    metrics: PortfolioMetrics,
  ) {
    val holdingsResult = calculateCurrentHoldings(transactions)
    val currentHoldings = holdingsResult.first
    val averageCost = holdingsResult.second

    if (currentHoldings <= BigDecimal.ZERO) return

    val price = dailyPriceService.getPrice(instrument, date)
    val currentValue = currentHoldings.multiply(price)

    val unrealizedProfit = transactions.sumOf { it.unrealizedProfit }

    updateMetrics(metrics, currentValue, unrealizedProfit)
    addXirrTransactions(metrics.xirrTransactions, transactions, currentValue, date)
  }

  private fun processInstrumentWithFallback(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    metrics: PortfolioMetrics,
  ) {
    val netQuantity = calculateNetQuantity(transactions)
    if (netQuantity <= BigDecimal.ZERO) return

    try {
      val price = dailyPriceService.getPrice(instrument, date)
      val currentValue = netQuantity.multiply(price)
      val instrumentProfit = calculateFallbackProfit(transactions, currentValue)

      updateMetrics(metrics, currentValue, instrumentProfit)
      addXirrTransactions(metrics.xirrTransactions, transactions, currentValue, date)
    } catch (e: NoSuchElementException) {
      log.warn("Skipping ${instrument.symbol} on $date: ${e.message}")
    }
  }

  private fun calculateNetQuantity(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions.fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }

  private fun calculateFallbackProfit(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
  ): BigDecimal {
    val totalBuys = calculateTotalBuys(transactions)
    val totalSells = calculateTotalSells(transactions)
    val realizedGains = calculateRealizedGains(transactions, totalBuys, totalSells)
    val unrealizedGains = calculateUnrealizedGains(transactions, currentValue, totalBuys)
    return realizedGains.add(unrealizedGains)
  }

  private fun calculateTotalBuys(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions
      .filter { it.transactionType == TransactionType.BUY }
      .sumOf { it.price.multiply(it.quantity).add(it.commission) }

  private fun calculateTotalSells(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions
      .filter { it.transactionType == TransactionType.SELL }
      .sumOf { it.price.multiply(it.quantity).subtract(it.commission) }

  private fun calculateRealizedGains(
    transactions: List<PortfolioTransaction>,
    totalBuys: BigDecimal,
    totalSells: BigDecimal,
  ): BigDecimal {
    val sellQuantity =
      transactions
      .filter { it.transactionType == TransactionType.SELL }
      .sumOf { it.quantity }

    val buyQuantity =
      transactions
      .filter { it.transactionType == TransactionType.BUY }
      .sumOf { it.quantity }

    return if (totalSells <= BigDecimal.ZERO || totalBuys <= BigDecimal.ZERO || buyQuantity <= BigDecimal.ZERO) {
      BigDecimal.ZERO
    } else {
      val avgBuyPrice = totalBuys.divide(buyQuantity, 10, RoundingMode.HALF_UP)
      totalSells.subtract(avgBuyPrice.multiply(sellQuantity))
    }
  }

  private fun calculateUnrealizedGains(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    totalBuys: BigDecimal,
  ): BigDecimal {
    val soldCost = calculateSoldCost(transactions, totalBuys)
    return currentValue.subtract(totalBuys.subtract(soldCost))
  }

  private fun calculateSoldCost(
    transactions: List<PortfolioTransaction>,
    totalBuys: BigDecimal,
  ): BigDecimal {
    val buyQuantity =
      transactions
      .filter { it.transactionType == TransactionType.BUY }
      .sumOf { it.quantity }

    if (buyQuantity <= BigDecimal.ZERO) return BigDecimal.ZERO

    val avgBuyPrice = totalBuys.divide(buyQuantity, 10, RoundingMode.HALF_UP)

    return transactions
      .filter { it.transactionType == TransactionType.SELL }
      .sumOf { avgBuyPrice.multiply(it.quantity) }
  }

  private fun updateMetrics(
    metrics: PortfolioMetrics,
    value: BigDecimal,
    profit: BigDecimal,
  ) {
    metrics.totalValue = metrics.totalValue.add(value)
    metrics.totalProfit = metrics.totalProfit.add(profit)
  }

  private fun addXirrTransactions(
    xirrList: MutableList<Transaction>,
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    date: LocalDate,
  ) {
    transactions.forEach { tx ->
      xirrList.add(convertToXirrTransaction(tx))
    }
    xirrList.add(Transaction(currentValue.toDouble(), date))
  }

  fun convertToXirrTransaction(tx: PortfolioTransaction): Transaction {
    val amount =
      when (tx.transactionType) {
        TransactionType.BUY -> -(tx.price.multiply(tx.quantity).add(tx.commission))
        TransactionType.SELL -> tx.price.multiply(tx.quantity).subtract(tx.commission)
      }
    return Transaction(amount.toDouble(), tx.transactionDate)
  }
}
