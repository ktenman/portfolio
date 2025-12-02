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
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Service
class InvestmentMetricsService(
  private val dailyPriceService: DailyPriceService,
  private val transactionService: TransactionService,
  private val clock: Clock,
) {
  private companion object {
    private val log = LoggerFactory.getLogger(InvestmentMetricsService::class.java)
  }

  private fun isToday(date: LocalDate): Boolean = date.isEqual(LocalDate.now(clock))

  fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, BigDecimal> {
    val (quantity, totalCost) =
      transactions
      .sortedWith(compareBy({ it.transactionDate }, { it.id }))
      .fold(HoldingsAccumulator()) { acc, tx ->
        when (tx.transactionType) {
          TransactionType.BUY -> acc.applyBuy(tx)
          TransactionType.SELL -> acc.applySell(tx)
        }
      }
    val averageCost =
      quantity
        .takeIf { it > BigDecimal.ZERO }
      ?.let { totalCost.divide(it, 10, RoundingMode.HALF_UP) }
      ?: BigDecimal.ZERO
    return quantity to averageCost
  }

  private data class HoldingsAccumulator(
    val quantity: BigDecimal = BigDecimal.ZERO,
    val totalCost: BigDecimal = BigDecimal.ZERO,
  ) {
    fun applyBuy(tx: PortfolioTransaction): HoldingsAccumulator {
      val cost = tx.price.multiply(tx.quantity).add(tx.commission)
      return copy(quantity = quantity.add(tx.quantity), totalCost = totalCost.add(cost))
    }

    fun applySell(tx: PortfolioTransaction): HoldingsAccumulator {
      if (quantity <= BigDecimal.ZERO) return this
      val sellRatio = tx.quantity.divide(quantity, 10, RoundingMode.HALF_UP)
      return copy(
        quantity = quantity.subtract(tx.quantity),
        totalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio)),
      )
    }
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
    val cashflows = transactions.map(::convertToXirrTransaction)
    val finalValue =
      currentValue
        .takeIf { it > BigDecimal.ZERO }
      ?.let { listOf(Transaction(it.toDouble(), calculationDate)) }
      ?: emptyList()
    return cashflows + finalValue
  }

  fun calculateAdjustedXirr(
    transactions: List<Transaction>,
    calculationDate: LocalDate = LocalDate.now(),
  ): Double {
    if (transactions.size < 2) return 0.0
    return runCatching {
      val xirrResult = Xirr(transactions).calculate()
      val cashFlows = transactions.filter { it.amount < 0 }
      if (cashFlows.isEmpty()) return@runCatching 0.0
      val weightedDays = calculateWeightedInvestmentAge(cashFlows, calculationDate)
      val dampingFactor = min(1.0, weightedDays / 60.0)
      xirrResult.coerceIn(-10.0, 10.0) * dampingFactor
    }.getOrElse {
      log.error("Error calculating adjusted XIRR", it)
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
    if (transactions.isEmpty()) return InstrumentMetrics.EMPTY
    val (totalHoldings, totalInvestment) = calculateAggregatedHoldings(transactions)
    val currentPrice = instrument.currentPrice ?: BigDecimal.ZERO
    val currentValue = calculateCurrentValue(totalHoldings, currentPrice)
    val realizedProfit =
      transactions
      .filter { it.transactionType == TransactionType.SELL }
      .sumOf { it.realizedProfit ?: BigDecimal.ZERO }
    val unrealizedProfit = currentValue.subtract(totalInvestment)
    val xirrTransactions = buildXirrTransactions(transactions, currentValue, calculationDate)
    return InstrumentMetrics(
      totalInvestment = totalInvestment,
      currentValue = currentValue,
      profit = realizedProfit.add(unrealizedProfit),
      realizedProfit = realizedProfit,
      unrealizedProfit = unrealizedProfit,
      xirr = calculateAdjustedXirr(xirrTransactions, calculationDate),
      quantity = totalHoldings,
    )
  }

  private fun calculateAggregatedHoldings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, BigDecimal> =
    transactions
    .groupBy { it.platform }
    .values
    .map { calculateCurrentHoldings(it) }
    .filter { (quantity, _) -> quantity > BigDecimal.ZERO }
    .fold(BigDecimal.ZERO to BigDecimal.ZERO) { (totalHoldings, totalInvestment), (quantity, avgCost) ->
      totalHoldings.add(quantity) to totalInvestment.add(quantity.multiply(avgCost))
    }

  fun calculateInstrumentMetricsWithProfits(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    calculationDate: LocalDate = LocalDate.now(),
  ): InstrumentMetrics {
    if (transactions.isEmpty()) return InstrumentMetrics.EMPTY
    val currentPrice = instrument.currentPrice ?: BigDecimal.ZERO
    transactionService.calculateTransactionProfits(transactions, currentPrice)
    return calculateInstrumentMetrics(instrument, transactions, calculationDate)
  }

  fun calculatePortfolioMetrics(
    instrumentGroups: Map<Instrument, List<PortfolioTransaction>>,
    date: LocalDate,
  ): PortfolioMetrics {
    val metrics = PortfolioMetrics()
    transactionService.calculateTransactionProfits(instrumentGroups.values.flatten())
    instrumentGroups.forEach { (instrument, transactions) ->
      runCatching {
        processInstrumentWithUnifiedCalculation(instrument, transactions, date, metrics)
      }.onFailure {
        log.warn("Unified calculation failed, using fallback: ${it.message}")
        processInstrumentWithFallback(instrument, transactions, date, metrics)
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
    val (currentHoldings, averageCost) = calculateCurrentHoldings(transactions)
    val realizedProfit =
      transactions
      .filter { it.transactionType == TransactionType.SELL }
      .sumOf { it.realizedProfit ?: BigDecimal.ZERO }
    val investment = currentHoldings.multiply(averageCost)
    val currentValue =
      when {
      currentHoldings <= BigDecimal.ZERO -> BigDecimal.ZERO
      isToday(date) -> currentHoldings.multiply(instrument.currentPrice ?: BigDecimal.ZERO)
      else -> currentHoldings.multiply(dailyPriceService.getPrice(instrument, date))
    }
    val unrealizedProfit = currentValue.subtract(investment)
    if (currentValue <= BigDecimal.ZERO && realizedProfit <= BigDecimal.ZERO) return
    updateMetricsWithSeparateProfits(metrics, currentValue, realizedProfit, unrealizedProfit)
    addXirrTransactions(metrics.xirrTransactions, transactions, currentValue, date)
  }

  private fun processInstrumentWithFallback(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    metrics: PortfolioMetrics,
  ) {
    val netQuantity = calculateNetQuantity(transactions)
    if (netQuantity <= BigDecimal.ZERO) {
      processZeroQuantityFallback(transactions, date, metrics)
      return
    }
    val price =
      runCatching { dailyPriceService.getPrice(instrument, date) }
      .onFailure { log.warn("Skipping ${instrument.symbol} on $date: ${it.message}") }
      .getOrNull() ?: return
    val currentValue = netQuantity.multiply(price)
    val (realizedProfit, unrealizedProfit) = calculateFallbackProfits(transactions, currentValue)
    if (currentValue <= BigDecimal.ZERO && realizedProfit <= BigDecimal.ZERO) return
    updateMetricsWithSeparateProfits(metrics, currentValue, realizedProfit, unrealizedProfit)
    addXirrTransactions(metrics.xirrTransactions, transactions, currentValue, date)
  }

  private fun processZeroQuantityFallback(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    metrics: PortfolioMetrics,
  ) {
    val (realizedProfit, unrealizedProfit) = calculateFallbackProfits(transactions, BigDecimal.ZERO)
    if (realizedProfit <= BigDecimal.ZERO) return
    updateMetricsWithSeparateProfits(metrics, BigDecimal.ZERO, realizedProfit, unrealizedProfit)
    addXirrTransactions(metrics.xirrTransactions, transactions, BigDecimal.ZERO, date)
  }

  private fun calculateNetQuantity(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions.fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }

  private fun calculateFallbackProfits(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
  ): Pair<BigDecimal, BigDecimal> {
    val totalBuys = calculateTotalBuys(transactions)
    val totalSells = calculateTotalSells(transactions)
    val realizedGains = calculateRealizedGains(transactions, totalBuys, totalSells)
    val unrealizedGains = calculateUnrealizedGains(transactions, currentValue, totalBuys)
    return Pair(realizedGains, unrealizedGains)
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
    if (totalSells <= BigDecimal.ZERO || totalBuys <= BigDecimal.ZERO || buyQuantity <= BigDecimal.ZERO) {
      return BigDecimal.ZERO
    }
    val avgBuyPrice = totalBuys.divide(buyQuantity, 10, RoundingMode.HALF_UP)
    return totalSells.subtract(avgBuyPrice.multiply(sellQuantity))
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

  private fun updateMetricsWithSeparateProfits(
    metrics: PortfolioMetrics,
    value: BigDecimal,
    realizedProfit: BigDecimal,
    unrealizedProfit: BigDecimal,
  ) {
    metrics.totalValue = metrics.totalValue.add(value)
    metrics.realizedProfit = metrics.realizedProfit.add(realizedProfit)
    metrics.unrealizedProfit = metrics.unrealizedProfit.add(unrealizedProfit)
    metrics.totalProfit = metrics.totalProfit.add(realizedProfit).add(unrealizedProfit)
  }

  private fun addXirrTransactions(
    xirrList: MutableList<Transaction>,
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    date: LocalDate,
  ) {
    xirrList += transactions.map(::convertToXirrTransaction)
    xirrList += Transaction(currentValue.toDouble(), date)
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
