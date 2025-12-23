package ee.tenman.portfolio.job

import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.LocalDate

@ScheduledJob
class BinanceDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val jobExecutionService: JobExecutionService,
  private val binanceService: BinanceService,
  private val dataProcessingUtil: DataProcessingUtil,
  private val dailyPriceService: DailyPriceService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Volatile
  private var isExecuting = false

  @Scheduled(fixedDelayString = "\${scheduling.jobs.binance-interval:120000}")
  fun runJob() {
    log.debug("Running Binance data retrieval job")
    jobExecutionService.executeJob(this)
    log.debug("Completed Binance data retrieval job")
  }

  override fun execute() {
    if (isExecuting) {
      log.warn("Binance data retrieval job is already running, skipping this execution")
      return
    }
    isExecuting = true
    try {
      executeJob()
    } finally {
      isExecuting = false
    }
  }

  private fun executeJob() {
    log.info("Starting Binance data retrieval job")
    val instruments =
      instrumentService.getAllInstrumentsWithoutFiltering().filter { it.providerName == ProviderName.BINANCE }
    if (instruments.isEmpty()) {
      log.info("No Binance instruments found to process")
      return
    }
    instruments.forEach { instrument -> processInstrument(instrument) }
    log.info("Completed Binance data retrieval job. Processed {} instruments", instruments.size)
  }

  private fun processInstrument(instrument: Instrument) {
    runCatching {
      if (dailyPriceService.hasHistoricalData(instrument)) {
        refreshCurrentPrice(instrument)
      } else {
        fetchFullHistory(instrument)
      }
    }.onFailure { e -> log.error("Error retrieving data for instrument {}", instrument.symbol, e) }
  }

  private fun refreshCurrentPrice(instrument: Instrument) {
    log.debug("Refreshing current price for instrument: {}", instrument.symbol)
    val currentPrice = binanceService.getCurrentPrice(instrument.symbol)
    val today = LocalDate.now(clock)
    dailyPriceService.saveCurrentPrice(instrument, currentPrice, today, ProviderName.BINANCE)
    instrumentService.updateCurrentPrice(instrument.id, currentPrice)
    log.debug("Updated current price for {}: {}", instrument.symbol, currentPrice)
  }

  private fun fetchFullHistory(instrument: Instrument) {
    log.info("Fetching full history for instrument: {} (no historical data found)", instrument.symbol)
    val dailyData = binanceService.getDailyPricesAsync(instrument.symbol)
    if (dailyData.isEmpty()) {
      log.warn("No daily data found for instrument: {}", instrument.symbol)
      return
    }
    dataProcessingUtil.processDailyData(
      instrument = instrument,
      dailyData = dailyData,
      providerName = ProviderName.BINANCE,
    )
  }
}
