package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.dto.InstrumentRollingXirrDto
import ee.tenman.portfolio.dto.PortfolioRollingXirrDto
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
  val processedDates: Int,
  val processedInstruments: Int,
  val failedCalculations: List<String> = emptyList(),
  val duration: Long,
)

@Service
class CalculationService(
  private val dataRetrievalService: DailyPriceService,
  private val instrumentRepository: InstrumentRepository,
  private val calculationDispatcher: CoroutineDispatcher,
  private val clock: Clock,
  private val portfolioSummaryService: SummaryService,
  private val transactionService: TransactionService,
  private val investmentMetricsService: InvestmentMetricsService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val AMOUNT_TO_SPEND = 1000.0
    private const val TICKER = "QDVE:GER:EUR"
    private val XIRR_DATA_SOURCE_MAPPING =
      mapOf(
      "WBIT:GER:EUR" to "BTCEUR",
      "SPYL:GER:EUR" to "VUAA:GER:EUR",
      "VNRT:AEX:EUR" to "VUAA:GER:EUR",
    )
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
    val instrument =
      instrumentRepository
        .findBySymbol(instrumentCode)
      .orElseThrow {
        ee.tenman.portfolio.exception
        .EntityNotFoundException("Instrument not found with symbol: $instrumentCode")
      }
    val allDailyPrices =
      dataRetrievalService
        .findAllByInstrument(instrument)
        .sortedBy { it.entryDate }

    if (allDailyPrices.size < 2) return emptyList()

    val xirrs = mutableListOf<Xirr>()
    var endDate = LocalDate.now(clock)
    val startDate = allDailyPrices.first().entryDate

    while (endDate.isAfter(startDate.plusMonths(1))) {
      val dailyPrices = allDailyPrices.filter { it.entryDate <= endDate }
      if (dailyPrices.size >= 2) {
        val firstPrice = dailyPrices.first()
        val lastPrice = dailyPrices.last()

        if (firstPrice.closePrice > BigDecimal.ZERO) {
          val sharesAmount =
            BigDecimal(AMOUNT_TO_SPEND)
            .divide(firstPrice.closePrice, 8, RoundingMode.HALF_UP)

          val currentValue = sharesAmount.multiply(lastPrice.closePrice)

          val transactions =
            listOf(
              Transaction(-AMOUNT_TO_SPEND, firstPrice.entryDate),
              Transaction(currentValue.toDouble(), lastPrice.entryDate),
            )

          if (currentValue > BigDecimal.ZERO) {
            xirrs.add(Xirr(transactions))
          }
        }
      }

      endDate = endDate.minusWeeks(4)
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

  @Cacheable(value = [ONE_DAY_CACHE], key = "'portfolio-rolling-xirr-v1'")
  fun calculatePortfolioRollingXirr(): PortfolioRollingXirrDto {
    log.info("Calculating portfolio-wide rolling XIRR")
    val transactions = transactionService.getAllTransactions()
    val instrumentGroups = transactions.groupBy { it.instrument }
    val holdingsWithQuantity =
      instrumentGroups.mapNotNull { (instrument, txns) ->
      val (quantity, _) = investmentMetricsService.calculateCurrentHoldings(txns)
      if (quantity > BigDecimal.ZERO) {
        val currentPrice = instrument.currentPrice ?: BigDecimal.ZERO
        val currentValue = quantity.multiply(currentPrice)
        Triple(instrument, txns, currentValue)
      } else {
        null
      }
    }
    if (holdingsWithQuantity.isEmpty()) {
      return PortfolioRollingXirrDto(
        instruments = emptyList(),
        portfolioAverageXirr = 0.0,
        portfolioWeightedXirr = 0.0,
        totalPortfolioValue = 0.0,
      )
    }
    val totalPortfolioValue =
      holdingsWithQuantity
      .fold(BigDecimal.ZERO) { acc, (_, _, value) -> acc.add(value) }
      .toDouble()
    val instrumentResults =
      runBlocking {
      holdingsWithQuantity
        .map { (instrument, _, currentValue) ->
        async(calculationDispatcher) {
          calculateInstrumentRollingXirr(instrument, totalPortfolioValue, currentValue.toDouble())
        }
      }.awaitAll()
    }
    val portfolioWeightedXirr = instrumentResults.sumOf { it.weightedXirr }
    val portfolioAverageXirr = instrumentResults.map { it.medianXirr }.average()
    return PortfolioRollingXirrDto(
      instruments = instrumentResults.sortedByDescending { it.currentValue },
      portfolioAverageXirr = portfolioAverageXirr,
      portfolioWeightedXirr = portfolioWeightedXirr,
      totalPortfolioValue = totalPortfolioValue,
    )
  }

  private fun calculateInstrumentRollingXirr(
    instrument: Instrument,
    totalPortfolioValue: Double,
    currentValue: Double,
  ): InstrumentRollingXirrDto {
    val dataSourceInstrument = resolveDataSourceInstrument(instrument)
    val xirrs = calculateRollingXirrForInstrument(dataSourceInstrument)
    val xirrValues =
      runBlocking {
      xirrs
        .map { xirr ->
        async(calculationDispatcher) {
          runCatching {
            val xirrValue = xirr.calculate()
            if (xirrValue > -1.0) {
              Transaction(xirrValue * 100.0, xirr.getTransactions().maxOf { it.date })
            } else {
              null
            }
          }.getOrNull()
        }
      }.awaitAll()
        .filterNotNull()
    }
    val medianXirr = if (xirrValues.isEmpty()) 0.0 else calculateMedian(xirrValues.map { it.amount })
    val portfolioWeight = if (totalPortfolioValue > 0) (currentValue / totalPortfolioValue) * 100 else 0.0
    val weightedXirr = (medianXirr * portfolioWeight) / 100.0
    return InstrumentRollingXirrDto(
      instrumentId = instrument.id!!,
      symbol = instrument.symbol,
      name = instrument.name,
      rollingXirrs = xirrValues,
      medianXirr = medianXirr,
      portfolioWeight = portfolioWeight,
      weightedXirr = weightedXirr,
      currentValue = currentValue,
    )
  }

  private fun resolveDataSourceInstrument(instrument: Instrument): Instrument {
    val mappedSymbol = XIRR_DATA_SOURCE_MAPPING[instrument.symbol] ?: return instrument
    return instrumentRepository.findBySymbol(mappedSymbol).orElse(instrument)
  }

  private fun calculateRollingXirrForInstrument(instrument: Instrument): List<Xirr> {
    val allDailyPrices =
      dataRetrievalService
      .findAllByInstrument(instrument)
      .sortedBy { it.entryDate }
    if (allDailyPrices.size < 2) return emptyList()
    val xirrs = mutableListOf<Xirr>()
    var endDate = LocalDate.now(clock)
    val startDate = allDailyPrices.first().entryDate
    while (endDate.isAfter(startDate.plusMonths(1))) {
      val dailyPrices = allDailyPrices.filter { it.entryDate <= endDate }
      if (dailyPrices.size >= 2) {
        val firstPrice = dailyPrices.first()
        val lastPrice = dailyPrices.last()
        if (firstPrice.closePrice > BigDecimal.ZERO) {
          val sharesAmount =
            BigDecimal(AMOUNT_TO_SPEND)
            .divide(firstPrice.closePrice, 8, RoundingMode.HALF_UP)
          val currentValue = sharesAmount.multiply(lastPrice.closePrice)
          val transactions =
            listOf(
            Transaction(-AMOUNT_TO_SPEND, firstPrice.entryDate),
            Transaction(currentValue.toDouble(), lastPrice.entryDate),
          )
          if (currentValue > BigDecimal.ZERO) {
            xirrs.add(Xirr(transactions))
          }
        }
      }
      endDate = endDate.minusWeeks(4)
    }
    return xirrs.filter { xirr ->
      runCatching {
        val result = xirr.calculate()
        result > -1.0
      }.getOrElse { false }
    }
  }
}
