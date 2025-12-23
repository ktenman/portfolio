package ee.tenman.portfolio.job

import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.pricing.DailyPriceService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class BinanceDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val jobExecutionService: JobExecutionService,
  private val binanceService: BinanceService,
  private val dataProcessingUtil: DataProcessingUtil,
  private val dailyPriceService: DailyPriceService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(fixedDelayString = "\${scheduling.jobs.binance-interval:120000}")
  fun runJob() {
    log.info("Running Binance data retrieval job")
    jobExecutionService.executeJob(this)
    log.info("Completed Binance data retrieval job")
  }

  override fun execute() {
    log.info("Starting Binance data retrieval job")
    val instruments = instrumentService.getAllInstrumentsWithoutFiltering().filter { it.providerName == ProviderName.BINANCE }
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
    log.info("Refreshing current price for instrument: {}", instrument.symbol)
    val currentPrice = binanceService.getCurrentPrice(instrument.symbol)
    instrumentService.updateCurrentPrice(instrument.id, currentPrice)
    log.info("Updated current price for {}: {}", instrument.symbol, currentPrice)
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
