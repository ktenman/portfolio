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
import java.time.LocalDate

private const val AMOUNT_TO_SPEND = 1000.0
private const val TICKER = "QDVE:GER:EUR"

@Service
class CalculatorService(
  private val dataRetrievalService: DailyPriceService,
  private val instrumentService: InstrumentService,
  private val calculationDispatcher: CoroutineDispatcher
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun calculateMedian(xirrs: List<Double>): Double {
    val sortedXirrs = xirrs.sorted()
    val size = sortedXirrs.size
    return if (size % 2 == 0) {
      val middleRight = size / 2
      val middleLeft = middleRight - 1
      (sortedXirrs[middleLeft] + sortedXirrs[middleRight]) / 2.0
    } else {
      sortedXirrs[size / 2]
    }
  }

  @Cacheable(value = [ONE_DAY_CACHE], key="'xirr-v3'")
  fun getCalculationResult(): CalculationResult {
    log.info("Calculating XIRR")
    val xirrs = calculateRollingXirr(TICKER).reversed()
    val xirrs2 = runBlocking {
      val deferredResults = xirrs.map { xirr ->
        async(calculationDispatcher) {
          Transaction(xirr.calculate() * 100.0, xirr.getTransactions().maxOf { it.date })
        }
      }
      deferredResults.awaitAll()
    }
    return CalculationResult(
      median = calculateMedian(xirrs2.map { it.amount }),
      average = xirrs2.map { it.amount }.average(),
      xirrs = xirrs2
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

      val (totalShares, transactions) = dailyPrices.fold(
        Pair(
          0.0,
          mutableListOf<Transaction>()
        )
      ) { (totalShares, transactions), price ->
        val sharesAmount = AMOUNT_TO_SPEND / price.closePrice.toDouble()
        transactions.add(Transaction(AMOUNT_TO_SPEND, price.entryDate))
        Pair(totalShares + sharesAmount, transactions)
      }

      val lastPrice = dailyPrices.last()
      val finalValue = -totalShares * lastPrice.closePrice.toDouble()
      transactions.add(Transaction(finalValue, lastPrice.entryDate))

      xirrs.add(Xirr(transactions))
      endDate = endDate.minusMonths(2)
    }

    return xirrs
  }


}
