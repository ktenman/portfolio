package ee.tenman.portfolio.job

import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.common.hasPositiveCloses
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.pricing.PriceSnapshotBackfillService
import ee.tenman.portfolio.service.pricing.PriceSnapshotService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@ScheduledJob
class BinanceDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val jobExecutionService: JobExecutionService,
  private val binanceService: BinanceService,
  private val dataProcessingUtil: DataProcessingUtil,
  private val dailyPriceService: DailyPriceService,
  private val priceSnapshotService: PriceSnapshotService,
  private val priceSnapshotBackfillService: PriceSnapshotBackfillService,
  private val clock: Clock,
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Volatile
  private var isExecuting = false

  @Scheduled(fixedDelayString = CollectionSchedules.BINANCE_PRICE_INTERVAL)
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
    val instruments = instrumentService.getInstrumentsByProvider(ProviderName.BINANCE)
    collectionMonitor.collect(CollectionKey.BINANCE_PRICES, instruments.map { it.symbol }) { run ->
      instruments.forEach { instrument -> processInstrument(instrument, run) }
    }
    log.info("Completed Binance data retrieval job. Processed ${instruments.size} instruments")
  }

  private fun processInstrument(
    instrument: Instrument,
    run: CollectionRun,
  ) {
    run.attempted(instrument.symbol)
    runCatching {
      if (dailyPriceService.hasHistoricalData(instrument)) {
        refreshCurrentPrice(instrument, run)
      } else {
        fetchFullHistory(instrument, run)
      }
    }.onFailure { e ->
      run.failed(instrument.symbol, e)
      log.error("Error retrieving data for instrument ${instrument.symbol}", e)
    }
  }

  private fun refreshCurrentPrice(
    instrument: Instrument,
    run: CollectionRun,
  ) {
    log.debug("Refreshing current price for instrument: ${instrument.symbol}")
    val currentPrice = binanceService.getCurrentPrice(instrument.symbol)
    require(currentPrice > BigDecimal.ZERO) { "Nonpositive Binance price for ${instrument.symbol}" }
    run.fetched(instrument.symbol)
    val today = LocalDate.now(clock)
    dailyPriceService.saveCurrentPrice(instrument, currentPrice, today, ProviderName.BINANCE)
    val backfillFailure =
      runCatching { priceSnapshotBackfillService.backfillFromBinance(instrument) }
        .onFailure { e -> log.warn("Failed to backfill snapshots for ${instrument.symbol}: ${e.message}") }
        .exceptionOrNull()
    priceSnapshotService.saveSnapshot(instrument, currentPrice, ProviderName.BINANCE)
    instrumentService.updateCurrentPrice(instrument.id, currentPrice)
    backfillFailure?.let { throw it }
    run.persisted(instrument.symbol)
    log.debug("Updated current price for ${instrument.symbol}: $currentPrice")
  }

  private fun fetchFullHistory(
    instrument: Instrument,
    run: CollectionRun,
  ) {
    log.info("Fetching full history for instrument: ${instrument.symbol} (no historical data found)")
    val dailyData = binanceService.getDailyPricesAsync(instrument.symbol)
    if (!dailyData.hasPositiveCloses()) {
      log.warn("No daily data found for instrument: ${instrument.symbol}")
      run.failed(instrument.symbol, IllegalArgumentException("Invalid Binance history for ${instrument.symbol}"))
      return
    }
    run.fetched(instrument.symbol)
    dataProcessingUtil.processDailyData(
      instrument = instrument,
      dailyData = dailyData,
      providerName = ProviderName.BINANCE,
    )
    run.persisted(instrument.symbol)
  }
}
