package ee.tenman.portfolio.service

import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

private const val AMOUNT_TO_SPEND = 1000.0
private const val TICKER = "QDVE:GER:EUR"

@Service
class CalculatorService(
  private val dataRetrievalService: DailyPriceService,
  private val instrumentService: InstrumentService
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

  fun calculateAverage(xirrs: List<Double>): Double {
    return if (xirrs.isEmpty()) {
      0.0 // or you could throw an exception here
    } else {
      xirrs.sum() / xirrs.size
    }
  }

  @PostConstruct
  fun init() {
    val xirrs = calculateRollingXirr(TICKER).reversed()
    val xirrs2 = runBlocking {
      val deferredResults = xirrs.map { xirr ->
        async(Dispatchers.Default) {
          xirr.calculate()
        }
      }
      deferredResults.awaitAll()
    }
    log.info("Median: " + calculateMedian(xirrs2))
    log.info("Average: " + calculateAverage(xirrs2))
  }

  fun calculateRollingXirr(instrumentCode: String): List<Xirr> {
    val instrument = instrumentService.getInstrument(instrumentCode)
    var dailyPrices = dataRetrievalService.findAllByInstrument(instrument).sortedBy { it.entryDate }

    var localDate = LocalDate.now()
    val xirrs = mutableListOf<Xirr>()

    do {
      val transactions = mutableListOf<Transaction>()
      var totalAmount = 0.0
      var spentAmount = 0.0

      dailyPrices = dailyPrices.filter { it.entryDate.isBefore(localDate) }

      if (dailyPrices.size < 2) break

      dailyPrices.forEach { price ->
        transactions.add(Transaction(AMOUNT_TO_SPEND, price.entryDate))
        val sharesAmount = AMOUNT_TO_SPEND / price.closePrice.toDouble()
        totalAmount += sharesAmount
        spentAmount += AMOUNT_TO_SPEND
      }

      val lastPrice = dailyPrices.last()
      val finalValue = -totalAmount * lastPrice.closePrice.toDouble()
      transactions.add(Transaction(finalValue, lastPrice.entryDate))

      val xirr = Xirr(transactions)
      xirrs.add(xirr)

      localDate = localDate.minusDays(1)
    } while (dailyPrices.isNotEmpty())

    return xirrs
  }



}
