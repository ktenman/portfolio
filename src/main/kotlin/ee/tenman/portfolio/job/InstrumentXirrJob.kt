package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.service.calculation.XirrCalculationService
import ee.tenman.portfolio.service.calculation.xirr.CashFlow
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import jakarta.annotation.PostConstruct
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@ScheduledJob
class InstrumentXirrJob(
  private val instrumentService: InstrumentService,
  private val dailyPriceService: DailyPriceService,
  private val xirrCalculationService: XirrCalculationService,
  private val jobExecutionService: JobExecutionService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct
  fun onStartup() {
    CompletableFuture.runAsync {
      Thread.sleep(TimeUnit.MINUTES.toMillis(STARTUP_DELAY_MINUTES))
      log.info("Running instrument XIRR job after startup delay")
      runJob()
    }
  }

  @Scheduled(cron = "0 45 6 * * *")
  fun runJob() {
    log.info("Running instrument XIRR job")
    jobExecutionService.executeJob(this)
    log.info("Completed instrument XIRR job")
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
        async(Dispatchers.Default) {
          calculateInstrumentXirr(instrument, calculationDate)
        }
      }.awaitAll()
    }
    val successCount = results.count { it.second }
    val failureCount = results.count { !it.second }
    log.info("Instrument XIRR calculation completed: {} succeeded, {} failed", successCount, failureCount)
  }

  private fun calculateInstrumentXirr(
    instrument: Instrument,
    calculationDate: LocalDate,
  ): Pair<String, Boolean> =
    runCatching {
    val dailyPrices = dailyPriceService.findAllByInstrument(instrument)
    if (dailyPrices.isEmpty()) {
      log.debug("No price history for {}", instrument.symbol)
      return instrument.symbol to true
    }
    val sortedPrices = dailyPrices.sortedBy { it.entryDate }
    val firstPriceDate = sortedPrices.first().entryDate
    val cashFlows = generateMonthlyCashFlows(firstPriceDate, calculationDate, sortedPrices, instrument)
    if (cashFlows.size < 2) {
      log.debug("Insufficient data for XIRR calculation for {}", instrument.symbol)
      instrumentService.updateXirrAnnualReturn(instrument.id, null)
      return instrument.symbol to true
    }
    val xirr = xirrCalculationService.calculateAdjustedXirr(cashFlows, calculationDate)
    val xirrValue = BigDecimal.valueOf(xirr).setScale(10, RoundingMode.HALF_UP)
    instrumentService.updateXirrAnnualReturn(instrument.id, xirrValue)
    instrument.symbol to true
  }.getOrElse { e ->
    log.warn("Failed to calculate XIRR for {}: {}", instrument.symbol, e.message)
    instrument.symbol to false
  }

  private fun generateMonthlyCashFlows(
    startDate: LocalDate,
    endDate: LocalDate,
    sortedPrices: List<ee.tenman.portfolio.domain.DailyPrice>,
    instrument: Instrument,
  ): List<CashFlow> {
    val cashFlows = mutableListOf<CashFlow>()
    var totalQuantity = BigDecimal.ZERO
    val priceByDate = sortedPrices.associateBy { it.entryDate }
    var currentDate = startDate.withDayOfMonth(1)
    while (!currentDate.isAfter(endDate)) {
      val price = findClosestPrice(currentDate, priceByDate, sortedPrices)
      if (price != null && price > BigDecimal.ZERO) {
        val quantity = MONTHLY_INVESTMENT.divide(price, 10, RoundingMode.HALF_UP)
        totalQuantity = totalQuantity.add(quantity)
        cashFlows.add(CashFlow(-MONTHLY_INVESTMENT.toDouble(), currentDate))
      }
      currentDate = currentDate.plusMonths(1)
    }
    if (totalQuantity > BigDecimal.ZERO) {
      val currentPrice = instrument.currentPrice ?: sortedPrices.last().closePrice
      val currentValue = totalQuantity.multiply(currentPrice)
      cashFlows.add(CashFlow(currentValue.toDouble(), endDate))
    }
    return cashFlows
  }

  private fun findClosestPrice(
    targetDate: LocalDate,
    priceByDate: Map<LocalDate, ee.tenman.portfolio.domain.DailyPrice>,
    sortedPrices: List<ee.tenman.portfolio.domain.DailyPrice>,
  ): BigDecimal? =
    priceByDate[targetDate]?.closePrice
      ?: findPriceWithinOffset(targetDate, priceByDate)
      ?: sortedPrices.filter { !it.entryDate.isAfter(targetDate) }.maxByOrNull { it.entryDate }?.closePrice

  private fun findPriceWithinOffset(
    targetDate: LocalDate,
    priceByDate: Map<LocalDate, ee.tenman.portfolio.domain.DailyPrice>,
  ): BigDecimal? {
    for (offset in 1..7) {
      priceByDate[targetDate.plusDays(offset.toLong())]?.let { return it.closePrice }
      priceByDate[targetDate.minusDays(offset.toLong())]?.let { return it.closePrice }
    }
    return null
  }

  companion object {
    private val MONTHLY_INVESTMENT = BigDecimal("1000")
    private const val STARTUP_DELAY_MINUTES = 2L
  }
}
