package ee.tenman.portfolio.job

import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.service.InstrumentService
import ee.tenman.portfolio.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class BinanceDataRetrievalJob(
  private val instrumentService: InstrumentService,
  private val jobExecutionService: JobExecutionService,
  private val binanceService: BinanceService,
  private val dataProcessingUtil: DataProcessingUtil,
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
    val instruments = instrumentService.findAll().filter { it.providerName == ProviderName.BINANCE }
    if (instruments.isEmpty()) {
      log.info("No Binance instruments found to process")
      return
    }

    instruments.forEach { fetch(it) }
    log.info("Completed Binance data retrieval job. Processed ${instruments.size} instruments.")
  }

  private fun fetch(instrument: Instrument) {
    runCatching {
      log.info("Retrieving data for instrument: ${instrument.symbol} (async yearly chunks)")
      val data = binanceService.getDailyPricesAsync(instrument.symbol)
      if (data.isEmpty()) {
        log.warn("No daily data found for instrument: ${instrument.symbol}")
        return
      }
      dataProcessingUtil.processDailyData(instrument, data, ProviderName.BINANCE)
    }.onFailure { log.error("Error retrieving data for instrument ${instrument.symbol}", it) }
  }
}
