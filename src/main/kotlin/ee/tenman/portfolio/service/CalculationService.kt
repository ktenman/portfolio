package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

data class CalculationResult(
  var xirrs: List<Transaction> = mutableListOf(),
  var median: Double = 0.0,
  var average: Double = 0.0,
  var total: BigDecimal = BigDecimal.ZERO,
) : Serializable

@Service
class CalculationService(
  private val dataRetrievalService: DailyPriceService,
  private val instrumentRepository: InstrumentRepository,
  private val calculationDispatcher: CoroutineDispatcher,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val AMOUNT_TO_SPEND = 1000.0
    private const val TICKER = "QDVE:GER:EUR"
  }

  fun calculateProfit(
    holdings: BigDecimal,
    averageCost: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal {
    val currentValue = holdings.multiply(currentPrice)
    val investment = holdings.multiply(averageCost)
    return currentValue.subtract(investment)
  }

  fun calculateCurrentValue(
    holdings: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal = holdings.multiply(currentPrice)

  fun calculateEarningsPerDay(
    totalValue: BigDecimal,
    xirrRate: BigDecimal,
  ): BigDecimal =
    totalValue
      .multiply(xirrRate)
      .divide(BigDecimal(365.25), 10, RoundingMode.HALF_UP)

  fun calculateAdjustedXirr(
    transactions: List<Transaction>,
    currentValue: BigDecimal,
    calculationDate: LocalDate = LocalDate.now(),
  ): Double {
    if (transactions.size < 2) {
      return 0.0
    }

    return try {
      val xirrResult = Xirr(transactions).calculate()
      val cashFlows = transactions.filter { it.amount < 0 }

      if (cashFlows.isEmpty()) {
        return 0.0
      }

      val weightedDays = calculateWeightedInvestmentAge(cashFlows, calculationDate)
      val dampingFactor = min(1.0, weightedDays / 60.0)
      val boundedXirr = xirrResult.coerceIn(-10.0, 10.0)

      boundedXirr * dampingFactor
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

  fun buildXirrTransactions(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
    calculationDate: LocalDate = LocalDate.now(),
  ): List<Transaction> {
    val cashflows =
      transactions.map { transaction ->
        val amount =
          when (transaction.transactionType) {
            TransactionType.BUY -> -(transaction.price * transaction.quantity + transaction.commission)
            TransactionType.SELL -> transaction.price * transaction.quantity - transaction.commission
          }
        Transaction(amount.toDouble(), transaction.transactionDate)
      }

    return if (currentValue > BigDecimal.ZERO) {
      cashflows + Transaction(currentValue.toDouble(), calculationDate)
    } else {
      cashflows
    }
  }

  @Cacheable(value = [ONE_DAY_CACHE], key = "'xirr-v3'")
  fun getCalculationResult(): CalculationResult {
    log.info("Calculating XIRR")
    val xirrs = calculateRollingXirr(TICKER).reversed()

    val xirrsResults =
      runBlocking {
        val deferredResults =
          xirrs.map { xirr ->
            async(calculationDispatcher) {
              val xirrValue = xirr.calculate()
              if (xirrValue > -1.0) {
                // XIRR returns a decimal (e.g., 0.22 for 22%), convert to percentage
                Transaction(xirrValue * 100.0, xirr.getTransactions().maxOf { it.date })
              } else {
                null
              }
            }
          }
        deferredResults.awaitAll().filterNotNull()
      }

    return CalculationResult(
      median = if (xirrsResults.isEmpty()) 0.0 else calculateMedian(xirrsResults.map { it.amount }),
      average = if (xirrsResults.isEmpty()) 0.0 else xirrsResults.map { it.amount }.average(),
      xirrs = xirrsResults,
      total = BigDecimal.ZERO,
    )
  }

  fun calculateMedian(xirrs: List<Double>): Double {
    val size = xirrs.size
    if (size == 0) return 0.0
    val sortedXirrs = xirrs.sorted()
    val middle = size / 2
    return if (size % 2 == 0) {
      (sortedXirrs[middle - 1] + sortedXirrs[middle]) / 2.0
    } else {
      sortedXirrs[middle]
    }
  }

  fun calculateRollingXirr(instrumentCode: String): List<Xirr> {
    val instrument =
      instrumentRepository
        .findBySymbol(instrumentCode)
      .orElseThrow { RuntimeException("Instrument not found with symbol: $instrumentCode") }
    val allDailyPrices =
      dataRetrievalService
        .findAllByInstrument(instrument)
        .sortedBy { it.entryDate }

    if (allDailyPrices.size < 2) return emptyList()

    val xirrs = mutableListOf<Xirr>()
    var endDate = LocalDate.now(clock)
    val startDate = allDailyPrices.first().entryDate

    while (endDate.isAfter(startDate.plusMonths(1))) {
      // Get prices for the time window from start to current endDate
      val dailyPrices = allDailyPrices.filter { it.entryDate <= endDate }
      if (dailyPrices.size < 2) break

      // Create a simple buy-and-hold transaction for this time window
      val firstPrice = dailyPrices.first()
      val lastPrice = dailyPrices.last()

      // Calculate how many shares we could buy with AMOUNT_TO_SPEND at the first price
      val sharesAmount =
        BigDecimal(AMOUNT_TO_SPEND)
        .divide(firstPrice.closePrice, 8, RoundingMode.HALF_UP)

      // Calculate the current value of those shares at the last price
      val currentValue = sharesAmount.multiply(lastPrice.closePrice)

      val transactions =
        mutableListOf(
        Transaction(-AMOUNT_TO_SPEND, firstPrice.entryDate),
        Transaction(currentValue.toDouble(), lastPrice.entryDate),
      )

      if (transactions.size == 2 && currentValue > BigDecimal.ZERO) {
        xirrs.add(Xirr(transactions))
      }

      endDate = endDate.minusWeeks(2)
    }

    return xirrs.filter { xirr ->
      try {
        val result = xirr.calculate()
        result > -1.0
      } catch (e: Exception) {
        log.error("Error calculating XIRR", e)
        false
      }
    }
  }
}
