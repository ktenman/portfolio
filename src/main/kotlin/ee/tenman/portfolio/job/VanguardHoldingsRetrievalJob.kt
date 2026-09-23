package ee.tenman.portfolio.job

import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.service.etf.EtfBreakdownService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.vanguard.VanguardFundSnapshot
import ee.tenman.portfolio.vanguard.VanguardHoldingsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.LocalDate

@ScheduledJob
class VanguardHoldingsRetrievalJob(
  private val vanguardHoldingsService: VanguardHoldingsService,
  private val etfHoldingService: EtfHoldingService,
  private val etfBreakdownService: EtfBreakdownService,
  private val etfHoldingsClassificationJob: EtfHoldingsClassificationJob,
  private val jobExecutionService: JobExecutionService,
  private val etfPositionRepository: EtfPositionRepository,
  private val lightyearPriceService: LightyearPriceService,
  private val clock: Clock,
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
    var changed = false
    var failure: Throwable? = null
    VanguardHoldingsService.FUNDS.forEach { (symbol, portId) ->
      runCatching {
        val snapshot = vanguardHoldingsService.fetchHoldings(portId)
        changed = save(symbol, snapshot) || changed
        changed = deleteNewerSnapshots(symbol, snapshot.effectiveDate) || changed
      }.onFailure { throwable ->
        log.error("Vanguard holdings import failed for $symbol", throwable)
        failure = failure ?: throwable
        changed = fallBack(symbol) || changed
      }
    }
    if (changed) etfBreakdownService.evictBreakdownCache()
    failure?.let { throw it }
  }

  private fun deleteNewerSnapshots(
    symbol: String,
    effectiveDate: LocalDate,
  ): Boolean {
    val deleted = etfPositionRepository.deleteBySymbolAndSnapshotDateAfter(symbol, effectiveDate)
    if (deleted > 0) log.info("Deleted $deleted positions of $symbol newer than $effectiveDate")
    return deleted > 0
  }

  private fun save(
    symbol: String,
    snapshot: VanguardFundSnapshot,
  ): Boolean {
    if (etfHoldingService.hasHoldingsForDate(symbol, snapshot.effectiveDate)) {
      log.info("Holdings for $symbol already exist for ${snapshot.effectiveDate}, skipping")
      return false
    }
    etfHoldingService.saveHoldings(symbol, snapshot.effectiveDate, snapshot.holdings)
    log.info("Saved ${snapshot.holdings.size} Vanguard holdings for $symbol on ${snapshot.effectiveDate}")
    return true
  }

  private fun fallBack(symbol: String): Boolean =
    runCatching { importFromLightyear(symbol) }
      .onFailure { log.error("Lightyear fallback failed for $symbol", it) }
      .getOrDefault(false)

  private fun importFromLightyear(symbol: String): Boolean {
    val today = LocalDate.now(clock)
    val latest = etfPositionRepository.findLatestSnapshotDate(symbol)
    if (latest != null && latest >= today.minusMonths(STALE_AFTER_MONTHS)) return false
    val holdings = lightyearPriceService.fetchHoldingsAsDto(symbol)
    if (holdings.isEmpty()) return false
    etfHoldingService.saveHoldings(symbol, today, holdings)
    log.warn("Saved ${holdings.size} Lightyear holdings for $symbol on $today because Vanguard failed")
    return true
  }

  companion object {
    private const val STALE_AFTER_MONTHS = 2L
  }
}
