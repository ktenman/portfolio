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

private const val SCALE = 10
private const val DAMPING_DAYS = 60.0
private const val XIRR_MIN = -10.0
private const val XIRR_MAX = 10.0

data class InstrumentMetrics(
  val investment: BigDecimal,
  val value: BigDecimal,
  val profit: BigDecimal,
  val realized: BigDecimal,
  val unrealized: BigDecimal,
  val xirr: Double,
  val quantity: BigDecimal,
) {
  @Deprecated("Use investment instead", ReplaceWith("investment"))
  val totalInvestment: BigDecimal get() = investment
  @Deprecated("Use value instead", ReplaceWith("value"))
  val currentValue: BigDecimal get() = value
  @Deprecated("Use realized instead", ReplaceWith("realized"))
  val realizedProfit: BigDecimal get() = realized
  @Deprecated("Use unrealized instead", ReplaceWith("unrealized"))
  val unrealizedProfit: BigDecimal get() = unrealized
  companion object {
    val EMPTY = InstrumentMetrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, BigDecimal.ZERO)
  }
}

data class PortfolioMetrics(
  var totalValue: BigDecimal = BigDecimal.ZERO,
  var realizedProfit: BigDecimal = BigDecimal.ZERO,
  var unrealizedProfit: BigDecimal = BigDecimal.ZERO,
  var totalProfit: BigDecimal = BigDecimal.ZERO,
  val xirrTransactions: MutableList<Transaction> = mutableListOf(),
)

data class Holdings(val quantity: BigDecimal, val cost: BigDecimal)

@Service
class InvestmentMetricsService(
  private val dailyPriceService: DailyPriceService,
  private val transactionService: TransactionService,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun holdings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, BigDecimal> {
    val result = transactions.sortedWith(compareBy({ it.transactionDate }, { it.id })).fold(Holdings(BigDecimal.ZERO, BigDecimal.ZERO)) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> Holdings(acc.quantity.add(tx.quantity), acc.cost.add(tx.price.multiply(tx.quantity).add(tx.commission)))
        TransactionType.SELL -> if (acc.quantity > BigDecimal.ZERO) {
          val ratio = tx.quantity.divide(acc.quantity, SCALE, RoundingMode.HALF_UP)
          Holdings(acc.quantity.subtract(tx.quantity), acc.cost.multiply(BigDecimal.ONE.subtract(ratio)))
        } else acc
      }
    }
    val average = if (result.quantity > BigDecimal.ZERO) result.cost.divide(result.quantity, SCALE, RoundingMode.HALF_UP) else BigDecimal.ZERO
    return Pair(result.quantity, average)
  }

  @Deprecated("Use holdings(transactions) instead", ReplaceWith("holdings(transactions)"))
  fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, BigDecimal> = holdings(transactions)

  fun value(quantity: BigDecimal, price: BigDecimal): BigDecimal = quantity.multiply(price)

  @Deprecated("Use value(quantity, price) instead", ReplaceWith("value(quantity, price)"))
  fun calculateCurrentValue(holdings: BigDecimal, currentPrice: BigDecimal): BigDecimal = value(holdings, currentPrice)

  fun profit(quantity: BigDecimal, cost: BigDecimal, price: BigDecimal): BigDecimal =
    quantity.multiply(price).subtract(quantity.multiply(cost))

  @Deprecated("Use profit(quantity, cost, price) instead", ReplaceWith("profit(quantity, cost, price)"))
  fun calculateProfit(holdings: BigDecimal, averageCost: BigDecimal, currentPrice: BigDecimal): BigDecimal =
    profit(holdings, averageCost, currentPrice)

  fun xirr(transactions: List<PortfolioTransaction>, value: BigDecimal, date: LocalDate = LocalDate.now()): List<Transaction> {
    val flows = transactions.map { convert(it) }
    return if (value > BigDecimal.ZERO) flows + Transaction(value.toDouble(), date) else flows
  }

  @Deprecated("Use xirr(transactions, value, date) instead", ReplaceWith("xirr(transactions, value, date)"))
  fun buildXirrTransactions(transactions: List<PortfolioTransaction>, currentValue: BigDecimal, calculationDate: LocalDate = LocalDate.now()): List<Transaction> =
    xirr(transactions, currentValue, calculationDate)

  fun adjusted(xirrTransactions: List<Transaction>, date: LocalDate = LocalDate.now()): Double {
    if (xirrTransactions.size < 2) return 0.0
    return runCatching {
      val result = Xirr(xirrTransactions).calculate()
      val flows = xirrTransactions.filter { it.amount < 0 }
      if (flows.isEmpty()) return 0.0
      val days = weighted(flows, date)
      val factor = min(1.0, days / DAMPING_DAYS)
      result.coerceIn(XIRR_MIN, XIRR_MAX) * factor
    }.onFailure { log.error("Error calculating adjusted XIRR", it) }.getOrDefault(0.0)
  }

  @Deprecated("Use adjusted(xirrTransactions, date) instead", ReplaceWith("adjusted(xirrTransactions, date)"))
  fun calculateAdjustedXirr(transactions: List<Transaction>, calculationDate: LocalDate = LocalDate.now()): Double =
    adjusted(transactions, calculationDate)

  fun metrics(instrument: Instrument, transactions: List<PortfolioTransaction>, date: LocalDate = LocalDate.now()): InstrumentMetrics {
    if (transactions.isEmpty()) return InstrumentMetrics.EMPTY
    val grouped = transactions.groupBy { it.platform }
    var investment = BigDecimal.ZERO
    var quantity = BigDecimal.ZERO
    grouped.values.forEach { platformTransactions ->
      val (qty, avg) = holdings(platformTransactions)
      if (qty > BigDecimal.ZERO) {
        investment = investment.add(qty.multiply(avg))
        quantity = quantity.add(qty)
      }
    }
    val price = instrument.currentPrice ?: BigDecimal.ZERO
    val current = value(quantity, price)
    val realized = transactions.filter { it.transactionType == TransactionType.SELL }.sumOf { it.realizedProfit ?: BigDecimal.ZERO }
    val unrealized = current.subtract(investment)
    val total = realized.add(unrealized)
    val xirrList = xirr(transactions, current, date)
    val rate = adjusted(xirrList, date)
    return InstrumentMetrics(investment, current, total, realized, unrealized, rate, quantity)
  }

  @Deprecated("Use metrics(instrument, transactions, date) instead", ReplaceWith("metrics(instrument, transactions, date)"))
  fun calculateInstrumentMetrics(instrument: Instrument, transactions: List<PortfolioTransaction>, calculationDate: LocalDate = LocalDate.now()): InstrumentMetrics =
    metrics(instrument, transactions, calculationDate)

  fun metricsWithProfits(instrument: Instrument, transactions: List<PortfolioTransaction>, date: LocalDate = LocalDate.now()): InstrumentMetrics {
    if (transactions.isEmpty()) return InstrumentMetrics.EMPTY
    val price = instrument.currentPrice ?: BigDecimal.ZERO
    transactionService.calculateProfits(transactions, price)
    return metrics(instrument, transactions, date)
  }

  @Deprecated("Use metricsWithProfits(instrument, transactions, date) instead", ReplaceWith("metricsWithProfits(instrument, transactions, date)"))
  fun calculateInstrumentMetricsWithProfits(instrument: Instrument, transactions: List<PortfolioTransaction>, calculationDate: LocalDate = LocalDate.now()): InstrumentMetrics =
    metricsWithProfits(instrument, transactions, calculationDate)

  fun calculatePortfolioMetrics(groups: Map<Instrument, List<PortfolioTransaction>>, date: LocalDate): PortfolioMetrics {
    val result = PortfolioMetrics()
    val all = groups.values.flatten()
    transactionService.calculateProfits(all)
    groups.forEach { (instrument, transactions) ->
      runCatching { unified(instrument, transactions, date, result) }
        .onFailure {
          log.warn("Unified calculation failed, using fallback: ${it.message}")
          fallback(instrument, transactions, date, result)
        }
    }
    return result
  }

  fun convert(tx: PortfolioTransaction): Transaction {
    val amount = when (tx.transactionType) {
      TransactionType.BUY -> -(tx.price.multiply(tx.quantity).add(tx.commission))
      TransactionType.SELL -> tx.price.multiply(tx.quantity).subtract(tx.commission)
    }
    return Transaction(amount.toDouble(), tx.transactionDate)
  }

  @Deprecated("Use convert(tx) instead", ReplaceWith("convert(tx)"))
  fun convertToXirrTransaction(tx: PortfolioTransaction): Transaction = convert(tx)

  private fun weighted(flows: List<Transaction>, date: LocalDate): Double {
    val total = flows.sumOf { -it.amount }
    return flows.sumOf { tx ->
      val weight = -tx.amount / total
      val days = ChronoUnit.DAYS.between(tx.date, date).toDouble()
      days * weight
    }
  }

  private fun isToday(date: LocalDate): Boolean = date.isEqual(LocalDate.now(clock))

  private fun unified(instrument: Instrument, transactions: List<PortfolioTransaction>, date: LocalDate, metrics: PortfolioMetrics) {
    val (quantity, average) = holdings(transactions)
    val realized = transactions.filter { it.transactionType == TransactionType.SELL }.sumOf { it.realizedProfit ?: BigDecimal.ZERO }
    val investment = quantity.multiply(average)
    val current = when {
      quantity <= BigDecimal.ZERO -> BigDecimal.ZERO
      isToday(date) -> quantity.multiply(instrument.currentPrice ?: BigDecimal.ZERO)
      else -> quantity.multiply(dailyPriceService.getPrice(instrument, date))
    }
    val unrealized = current.subtract(investment)
    if (current > BigDecimal.ZERO || realized > BigDecimal.ZERO) {
      update(metrics, current, realized, unrealized)
      append(metrics.xirrTransactions, transactions, current, date)
    }
  }

  private fun fallback(instrument: Instrument, transactions: List<PortfolioTransaction>, date: LocalDate, metrics: PortfolioMetrics) {
    val net = quantity(transactions)
    if (net <= BigDecimal.ZERO) {
      val (realized, unrealized) = profits(transactions, BigDecimal.ZERO)
      if (realized > BigDecimal.ZERO) {
        update(metrics, BigDecimal.ZERO, realized, unrealized)
        append(metrics.xirrTransactions, transactions, BigDecimal.ZERO, date)
      }
      return
    }
    val price = runCatching { dailyPriceService.getPrice(instrument, date) }
      .onFailure { log.warn("Skipping ${instrument.symbol} on $date: ${it.message}") }
      .getOrNull() ?: return
    val current = net.multiply(price)
    val (realized, unrealized) = profits(transactions, current)
    if (current > BigDecimal.ZERO || realized > BigDecimal.ZERO) {
      update(metrics, current, realized, unrealized)
      append(metrics.xirrTransactions, transactions, current, date)
    }
  }

  private fun quantity(transactions: List<PortfolioTransaction>): BigDecimal =
    transactions.fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }

  private fun profits(transactions: List<PortfolioTransaction>, current: BigDecimal): Pair<BigDecimal, BigDecimal> {
    val buys = transactions.filter { it.transactionType == TransactionType.BUY }
    val sells = transactions.filter { it.transactionType == TransactionType.SELL }
    val buyTotal = buys.sumOf { it.price.multiply(it.quantity).add(it.commission) }
    val sellTotal = sells.sumOf { it.price.multiply(it.quantity).subtract(it.commission) }
    val buyQty = buys.sumOf { it.quantity }
    val sellQty = sells.sumOf { it.quantity }
    val realized = if (sellTotal <= BigDecimal.ZERO || buyTotal <= BigDecimal.ZERO || buyQty <= BigDecimal.ZERO) BigDecimal.ZERO
    else {
      val avg = buyTotal.divide(buyQty, SCALE, RoundingMode.HALF_UP)
      sellTotal.subtract(avg.multiply(sellQty))
    }
    val soldCost = if (buyQty <= BigDecimal.ZERO) BigDecimal.ZERO else {
      val avg = buyTotal.divide(buyQty, SCALE, RoundingMode.HALF_UP)
      sells.sumOf { avg.multiply(it.quantity) }
    }
    val unrealized = current.subtract(buyTotal.subtract(soldCost))
    return Pair(realized, unrealized)
  }

  private fun update(metrics: PortfolioMetrics, value: BigDecimal, realized: BigDecimal, unrealized: BigDecimal) {
    metrics.totalValue = metrics.totalValue.add(value)
    metrics.realizedProfit = metrics.realizedProfit.add(realized)
    metrics.unrealizedProfit = metrics.unrealizedProfit.add(unrealized)
    metrics.totalProfit = metrics.totalProfit.add(realized).add(unrealized)
  }

  private fun append(xirrList: MutableList<Transaction>, transactions: List<PortfolioTransaction>, value: BigDecimal, date: LocalDate) {
    transactions.forEach { xirrList.add(convert(it)) }
    xirrList.add(Transaction(value.toDouble(), date))
  }
}
