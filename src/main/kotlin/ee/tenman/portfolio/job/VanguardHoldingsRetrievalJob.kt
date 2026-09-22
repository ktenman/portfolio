package ee.tenman.portfolio.job

import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.vanguard.VanguardHoldingsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

@ScheduledJob
class VanguardHoldingsRetrievalJob(
  private val vanguardHoldingsService: VanguardHoldingsService,
  private val etfHoldingService: EtfHoldingService,
  private val etfBreakdownService: EtfBreakdownService,
  private val etfHoldingsClassificationJob: EtfHoldingsClassificationJob,
  private val jobExecutionService: JobExecutionService,
  private val etfPositionRepository: EtfPositionRepository,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 60000, fixedDelay = Long.MAX_VALUE)
  fun runStartupImport() {
    if (VanguardHoldingsService.FUNDS.keys.all { etfPositionRepository.existsByEtfInstrumentSymbol(it) }) {
      log.info("Vanguard funds already have positions, skipping startup import")
      return
    }
    runCatching { jobExecutionService.executeJob(this) }
      .onFailure { log.error("Vanguard holdings startup import failed", it) }
    log.info("Classifying sectors for newly imported Vanguard holdings")
    jobExecutionService.executeJob(etfHoldingsClassificationJob)
  }

  @Scheduled(cron = "0 30 2 * * *")
  fun runNightlyImport() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    var saved = false
    var failure: Throwable? = null
    VanguardHoldingsService.FUNDS.forEach { (symbol, portId) ->
      runCatching { importFund(symbol, portId) }
        .onSuccess { saved = saved || it }
        .onFailure { throwable ->
          log.error("Vanguard holdings import failed for $symbol", throwable)
          failure = failure ?: throwable
        }
    }
    if (saved) etfBreakdownService.evictBreakdownCache()
    failure?.let { throw it }
  }

  private fun importFund(
    symbol: String,
    portId: String,
  ): Boolean {
    val snapshot = vanguardHoldingsService.fetchHoldings(portId)
    if (etfHoldingService.hasHoldingsForDate(symbol, snapshot.effectiveDate)) {
      log.info("Holdings for $symbol already exist for ${snapshot.effectiveDate}, skipping")
      return false
    }
    etfHoldingService.saveHoldings(symbol, snapshot.effectiveDate, snapshot.holdings)
    log.info("Saved ${snapshot.holdings.size} Vanguard holdings for $symbol on ${snapshot.effectiveDate}")
    return true
  }
}
