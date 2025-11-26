package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.exception.EntityNotFoundException
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.xirr.Transaction
import ee.tenman.portfolio.service.xirr.Xirr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

private const val AMOUNT = 1000.0
private const val TICKER = "QDVE:GER:EUR"
private const val SCALE = 8

data class CalculationResult(
  var xirrs: List<Transaction> = mutableListOf(),
  var median: Double = 0.0,
  var average: Double = 0.0,
  var total: BigDecimal = BigDecimal.ZERO,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}

data class XirrCalculationResult(
  val dates: Int,
  val instruments: Int,
  val failures: List<String> = emptyList(),
  val duration: Long,
) {
  @Deprecated("Use dates instead", ReplaceWith("dates"))
  val processedDates: Int get() = dates
  @Deprecated("Use instruments instead", ReplaceWith("instruments"))
  val processedInstruments: Int get() = instruments
  @Deprecated("Use failures instead", ReplaceWith("failures"))
  val failedCalculations: List<String> get() = failures
}

@Service
class CalculationService(
  private val dailyPriceService: DailyPriceService,
  private val instrumentRepository: InstrumentRepository,
  private val dispatcher: CoroutineDispatcher,
  private val clock: Clock,
  private val summaryService: SummaryService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Cacheable(value = [ONE_DAY_CACHE], key = "'xirr-v3'")
  fun result(): CalculationResult {
    log.info("Calculating XIRR")
    val xirrs = rolling(TICKER).reversed()
    val results = runBlocking {
      xirrs.map { xirr ->
        async(dispatcher) {
          val value = xirr.calculate()
          if (value > -1.0) Transaction(value * 100.0, xirr.getTransactions().maxOf { it.date }) else null
        }
      }.awaitAll().filterNotNull()
    }
    return CalculationResult(
      median = if (results.isEmpty()) 0.0 else median(results.map { it.amount }),
      average = if (results.isEmpty()) 0.0 else results.map { it.amount }.average(),
      xirrs = results,
      total = BigDecimal.ZERO,
    )
  }

  @Cacheable(value = [ONE_DAY_CACHE], key = "'xirr-v3'")
  @Deprecated("Use result() instead", ReplaceWith("result()"))
  fun getCalculationResult(): CalculationResult = result()

  fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val middle = values.size / 2
    return if (values.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
  }

  @Deprecated("Use median(values) instead", ReplaceWith("median(values)"))
  fun calculateMedian(xirrs: List<Double>): Double = median(xirrs)

  fun rolling(code: String): List<Xirr> {
    val instrument = instrumentRepository.findBySymbol(code).orElseThrow { EntityNotFoundException("Instrument not found with symbol: $code") }
    val all = dailyPriceService.findAllByInstrument(instrument).sortedBy { it.entryDate }
    if (all.size < 2) return emptyList()
    val start = all.first().entryDate
    return generateSequence(LocalDate.now(clock)) { it.minusWeeks(4) }
      .takeWhile { it.isAfter(start.plusMonths(1)) }
      .mapNotNull { end ->
        val filtered = all.filter { it.entryDate <= end }
        if (filtered.size < 2) return@mapNotNull null
        val first = filtered.first()
        val last = filtered.last()
        if (first.closePrice <= BigDecimal.ZERO) return@mapNotNull null
        val shares = BigDecimal(AMOUNT).divide(first.closePrice, SCALE, RoundingMode.HALF_UP)
        val value = shares.multiply(last.closePrice)
        if (value <= BigDecimal.ZERO) return@mapNotNull null
        Xirr(listOf(Transaction(-AMOUNT, first.entryDate), Transaction(value.toDouble(), last.entryDate)))
      }
      .filter { xirr -> runCatching { xirr.calculate() > -1.0 }.onFailure { log.error("Error calculating XIRR", it) }.getOrDefault(false) }
      .toList()
  }

  @Deprecated("Use rolling(code) instead", ReplaceWith("rolling(code)"))
  fun calculateRollingXirr(instrumentCode: String): List<Xirr> = rolling(instrumentCode)

  suspend fun batch(dates: List<LocalDate>): XirrCalculationResult = coroutineScope {
    val start = System.currentTimeMillis()
    val results = dates.map { date ->
      async(dispatcher) {
        runCatching {
          val summary = summaryService.calculateSummaryForDate(date)
          summaryService.saveDailySummary(summary)
          date to null
        }.getOrElse { date to "Failed for date $date: ${it.message}" }
      }
    }.awaitAll()
    val failures = results.mapNotNull { it.second }
    val success = results.count { it.second == null }
    XirrCalculationResult(success, 0, failures, System.currentTimeMillis() - start)
  }

  @Deprecated("Use batch(dates) instead", ReplaceWith("batch(dates)"))
  suspend fun calculateBatchXirrAsync(dates: List<LocalDate>): XirrCalculationResult = batch(dates)
}
