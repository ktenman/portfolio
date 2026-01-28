package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.common.orNotFoundBySymbol
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.dto.CalculationResult
import ee.tenman.portfolio.model.XirrCalculationResult
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.calculation.xirr.Xirr
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.summary.SummaryService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

@Service
class CalculationService(
  private val dataRetrievalService: DailyPriceService,
  private val instrumentRepository: InstrumentRepository,
  private val calculationDispatcher: CoroutineDispatcher,
  private val clock: Clock,
  private val portfolioSummaryService: SummaryService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val AMOUNT_TO_SPEND = 1000.0
    private const val TICKER = "QDVE:GER:EUR"
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
                CashFlow(xirrValue * 100.0, xirr.getCashFlows().maxOf { it.date })
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
      cashFlows = xirrsResults,
      total = BigDecimal.ZERO,
    )
  }

  fun calculateMedian(xirrs: List<Double>): Double {
    if (xirrs.isEmpty()) return 0.0

    val sortedXirrs = xirrs.sorted()
    val middle = xirrs.size / 2
    return if (xirrs.size % 2 == 0) {
      (sortedXirrs[middle - 1] + sortedXirrs[middle]) / 2.0
    } else {
      sortedXirrs[middle]
    }
  }

  fun calculateRollingXirr(instrumentCode: String): List<Xirr> {
    val instrument = instrumentRepository.findBySymbol(instrumentCode).orNotFoundBySymbol("Instrument", instrumentCode)
    val allDailyPrices = dataRetrievalService.findAllByInstrument(instrument).sortedBy { it.entryDate }
    if (allDailyPrices.size < 2) return emptyList()
    val startDate = allDailyPrices.first().entryDate
    val cutoffDate = startDate.plusMonths(1)
    return generateSequence(LocalDate.now(clock)) { it.minusWeeks(4) }
      .takeWhile { it.isAfter(cutoffDate) }
      .mapNotNull { endDate -> calculateXirrForPeriod(allDailyPrices, endDate) }
      .filter { xirr -> isValidXirr(xirr) }
      .toList()
  }

  private fun isValidXirr(xirr: Xirr): Boolean =
    runCatching { xirr.calculate() > -1.0 }
      .onFailure { log.error("Error calculating XIRR", it) }
      .getOrDefault(false)

  private fun calculateXirrForPeriod(
    allDailyPrices: List<DailyPrice>,
    endDate: LocalDate,
  ): Xirr? {
    val dailyPrices = allDailyPrices.filter { it.entryDate <= endDate }
    if (dailyPrices.size < 2) return null
    val firstPrice = dailyPrices.first()
    val lastPrice = dailyPrices.last()
    return firstPrice.closePrice
      .takeIf { it > BigDecimal.ZERO }
      ?.let { closePrice -> BigDecimal(AMOUNT_TO_SPEND).divide(closePrice, 8, RoundingMode.HALF_UP) }
      ?.multiply(lastPrice.closePrice)
      ?.takeIf { it > BigDecimal.ZERO }
      ?.let { currentValue ->
        Xirr(
          listOf(
            CashFlow(-AMOUNT_TO_SPEND, firstPrice.entryDate),
            CashFlow(currentValue.toDouble(), lastPrice.entryDate),
          ),
        )
      }
  }

  suspend fun calculateBatchXirrAsync(dates: List<LocalDate>): XirrCalculationResult =
    coroutineScope {
      val startTime = System.currentTimeMillis()

      val results =
        dates
          .map { date ->
            async(calculationDispatcher) {
              runCatching {
                val summary = portfolioSummaryService.calculateSummaryForDate(date)
                portfolioSummaryService.saveDailySummary(summary)
                date to null
              }.getOrElse { exception ->
                date to "Failed for date $date: ${exception.message}"
              }
            }
          }.awaitAll()

      val failures = results.mapNotNull { it.second }
      val successCount = results.count { it.second == null }

      XirrCalculationResult(
        processedDates = successCount,
        processedInstruments = 0,
        failedCalculations = failures,
        duration = System.currentTimeMillis() - startTime,
      )
    }
}
