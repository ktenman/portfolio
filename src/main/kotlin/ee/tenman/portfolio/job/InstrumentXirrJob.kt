package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

@ScheduledJob
class InstrumentXirrJob(
  private val instrumentService: InstrumentService,
  private val dailyPriceService: DailyPriceService,
  private val xirrCalculationService: XirrCalculationService,
  private val jobExecutionService: JobExecutionService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = STARTUP_DELAY_MS, fixedDelay = Long.MAX_VALUE)
  fun onStartup() {
    runJob()
  }

  @Scheduled(cron = "0 45 6 * * *")
  fun runJob() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    log.info("Starting instrument XIRR calculation with synthetic DCA")
    val instruments = instrumentService.getAllInstrumentsWithoutFiltering()
    if (instruments.isEmpty()) {
      log.info("No instruments found. Skipping instrument XIRR calculation")
      return
    }
    val calculationDate = LocalDate.now(clock)
    val results =
      runBlocking {
      instruments
        .map { instrument ->
        async(Dispatchers.IO) {
          calculateInstrumentXirr(instrument, calculationDate)
        }
      }.awaitAll()
    }
    val successCount = results.count { it.second }
    val failureCount = results.count { !it.second }
    log.info("Instrument XIRR calculation completed: $successCount succeeded, $failureCount failed")
  }

  private fun calculateInstrumentXirr(
    instrument: Instrument,
    calculationDate: LocalDate,
  ): Pair<String, Boolean> =
    runCatching {
    val dailyPrices = dailyPriceService.findAllByInstrument(instrument)
    if (dailyPrices.isEmpty()) {
      log.debug("No price history for ${instrument.symbol}")
      return instrument.symbol to true
    }
    val sortedPrices = dailyPrices.sortedBy { it.entryDate }
    val firstPriceDate = sortedPrices.first().entryDate
    val cashFlows = generateMonthlyCashFlows(firstPriceDate, calculationDate, sortedPrices, instrument)
    if (cashFlows.size < 2) {
      log.debug("Insufficient data for XIRR calculation for ${instrument.symbol}")
      instrumentService.updateXirrAnnualReturn(instrument.id, null)
      return instrument.symbol to true
    }
    val xirr = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
    val xirrValue = xirr?.let { BigDecimal.valueOf(it).setScale(10, RoundingMode.HALF_UP) }
    instrumentService.updateXirrAnnualReturn(instrument.id, xirrValue)
    instrument.symbol to true
  }.getOrElse { e ->
    log.warn("Failed to calculate XIRR for ${instrument.symbol}: ${e.message}")
    instrument.symbol to false
  }

  private fun generateMonthlyCashFlows(
    startDate: LocalDate,
    endDate: LocalDate,
    sortedPrices: List<DailyPrice>,
    instrument: Instrument,
  ): List<CashFlow> {
    val priceByDate = sortedPrices.associateBy { it.entryDate }
    var totalQuantity = BigDecimal.ZERO
    val cashFlows =
      generateSequence(startDate.withDayOfMonth(1)) { it.plusMonths(1) }
      .takeWhile { !it.isAfter(endDate) }
      .mapNotNull { date ->
        val price = findClosestPrice(date, priceByDate, sortedPrices)
        if (price != null && price > BigDecimal.ZERO) {
          totalQuantity = totalQuantity.add(MONTHLY_INVESTMENT.divide(price, 10, RoundingMode.HALF_UP))
          CashFlow(-MONTHLY_INVESTMENT.toDouble(), date)
        } else {
          null
        }
      }.toMutableList()
    if (totalQuantity > BigDecimal.ZERO) {
      val currentPrice = instrument.currentPrice ?: sortedPrices.last().closePrice
      cashFlows.add(CashFlow(totalQuantity.multiply(currentPrice).toDouble(), endDate))
    }
    return cashFlows
  }

  private fun findClosestPrice(
    targetDate: LocalDate,
    priceByDate: Map<LocalDate, DailyPrice>,
    sortedPrices: List<DailyPrice>,
  ): BigDecimal? =
    priceByDate[targetDate]?.closePrice
      ?: findPriceWithinOffset(targetDate, priceByDate)
      ?: sortedPrices.lastOrNull { !it.entryDate.isAfter(targetDate) }?.closePrice

  private fun findPriceWithinOffset(
    targetDate: LocalDate,
    priceByDate: Map<LocalDate, DailyPrice>,
  ): BigDecimal? =
    (1..MAX_PRICE_OFFSET_DAYS).firstNotNullOfOrNull { offset ->
      priceByDate[targetDate.plusDays(offset.toLong())]?.closePrice
        ?: priceByDate[targetDate.minusDays(offset.toLong())]?.closePrice
    }

  companion object {
    private val MONTHLY_INVESTMENT = BigDecimal("1000")
    private const val STARTUP_DELAY_MS = 120_000L
    private const val MAX_PRICE_OFFSET_DAYS = 7
  }
}
