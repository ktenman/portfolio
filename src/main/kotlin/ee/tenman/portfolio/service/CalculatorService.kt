package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private const val AMOUNT_TO_SPEND = 1000.0
private const val TICKER = "QDVE:GER:EUR"

@Service
class CalculatorService(
  private val dataRetrievalService: DailyPriceService,
  private val instrumentService: InstrumentService,
  private val calculationDispatcher: CoroutineDispatcher,
  private val portfolioSummaryService: PortfolioSummaryService
) {
  private val log = LoggerFactory.getLogger(javaClass)

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

  @Cacheable(value = [ONE_DAY_CACHE], key = "'xirr-v18'")
  fun getCalculationResult(): CalculationResult {
    log.info("Calculating XIRR")
    val xirrs = calculateRollingXirr(TICKER).reversed()

    val xirrsResults = runBlocking {
      val deferredResults = xirrs.map { xirr ->
        async(calculationDispatcher) {
          val xirrValue = xirr.calculate()
          // Only include positive or reasonable negative returns
          if (xirrValue > -1.0) {
            Transaction(xirrValue * 100.0, xirr.getTransactions().maxOf { it.date })
          } else null
        }
      }
      deferredResults.awaitAll().filterNotNull()
    }

    val totalCurrentValue = portfolioSummaryService.getCurrentDaySummary().totalValue

    return CalculationResult(
      median = if (xirrsResults.isEmpty()) 0.0 else calculateMedian(xirrsResults.map { it.amount }),
      average = if (xirrsResults.isEmpty()) 0.0 else xirrsResults.map { it.amount }.average(),
      xirrs = xirrsResults,
      total = totalCurrentValue
    )
  }

  fun calculateRollingXirr(instrumentCode: String): List<Xirr> {
    val instrument = instrumentService.getInstrument(instrumentCode)
    val allDailyPrices = dataRetrievalService.findAllByInstrument(instrument)
      .sortedBy { it.entryDate }

    if (allDailyPrices.size < 2) return emptyList()

    val xirrs = mutableListOf<Xirr>()
    var endDate = LocalDate.now()
    val startDate = allDailyPrices.first().entryDate

    while (endDate.isAfter(startDate)) {
      val dailyPrices = allDailyPrices.takeWhile { it.entryDate <= endDate }
      if (dailyPrices.size < 2) break

      // Calculate investment periods
      val transactions = mutableListOf<Transaction>()
      var remainingShares = BigDecimal.ZERO
      var cumulativeInvestment = BigDecimal.ZERO

      // Process all prices up to current date
      for (price in dailyPrices) {
        val sharesAmount = BigDecimal(AMOUNT_TO_SPEND)
          .divide(price.closePrice, 8, RoundingMode.HALF_UP)
        remainingShares += sharesAmount

        if (remainingShares > BigDecimal.ZERO) {
          cumulativeInvestment = cumulativeInvestment.add(BigDecimal(AMOUNT_TO_SPEND))
          transactions.add(Transaction(-AMOUNT_TO_SPEND, price.entryDate))
        }
      }

      // Only calculate XIRR if we have remaining shares
      if (remainingShares > BigDecimal.ZERO) {
        val lastPrice = dailyPrices.last()
        val currentValue = remainingShares.multiply(lastPrice.closePrice)
        if (currentValue > BigDecimal.ZERO) {
          transactions.add(Transaction(currentValue.toDouble(), lastPrice.entryDate))

          // Only add to XIRR list if we have more than one transaction
          if (transactions.size > 1) {
            xirrs.add(Xirr(transactions))
          }
        }
      }

      endDate = endDate.minusWeeks(2)
    }

    return xirrs.filter { xirr ->
      try {
        val result = xirr.calculate()
        result > -1.0 // Filter out extreme negative values
      } catch (e: Exception) {
        log.error("Error calculating XIRR", e)
        false
      }
    }
  }
}
