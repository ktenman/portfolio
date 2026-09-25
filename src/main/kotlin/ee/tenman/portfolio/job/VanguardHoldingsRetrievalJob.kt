package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.domain.VanguardCountryUpdate
import ee.tenman.portfolio.domain.VanguardIndustryUpdate
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionSchedules
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.service.etf.EtfHoldingCountryService
import ee.tenman.portfolio.service.etf.EtfHoldingIndustryService
import ee.tenman.portfolio.service.etf.EtfHoldingService
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
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
  private val cacheInvalidationService: CacheInvalidationService,
  private val etfHoldingIndustryService: EtfHoldingIndustryService,
  private val etfHoldingCountryService: EtfHoldingCountryService,
  private val etfHoldingsClassificationJob: EtfHoldingsClassificationJob,
  private val jobExecutionService: JobExecutionService,
  private val etfPositionRepository: EtfPositionRepository,
  private val lightyearPriceService: LightyearPriceService,
  private val clock: Clock,
  private val collectionMonitor: CollectionMonitorService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = CollectionSchedules.VANGUARD_HOLDINGS_STARTUP_SECONDS * 1000, fixedDelay = Long.MAX_VALUE)
  fun runStartupImport() {
    runCatching { jobExecutionService.executeJob(this) }
      .onFailure { log.error("Vanguard holdings startup import failed", it) }
    log.info("Classifying sectors for newly imported Vanguard holdings")
    jobExecutionService.executeJob(etfHoldingsClassificationJob)
  }

  @Scheduled(cron = CollectionSchedules.VANGUARD_HOLDINGS_CRON, zone = CollectionSchedules.TIME_ZONE)
  fun runNightlyImport() {
    jobExecutionService.executeJob(this)
  }

  override fun execute() {
    collectionMonitor.collect(CollectionKey.VANGUARD_HOLDINGS, VanguardHoldingsService.FUNDS.keys) { run ->
      executeCollection(run)
    }
  }

  private fun executeCollection(run: CollectionRun) {
    var changed = false
    var failure: Throwable? = null
    val industries = mutableListOf<VanguardIndustryUpdate>()
    val countries = mutableListOf<VanguardCountryUpdate>()
    VanguardHoldingsService.FUNDS.forEach { (symbol, portId) ->
      run.attempted(symbol)
      runCatching {
        val snapshot = vanguardHoldingsService.fetchHoldings(portId)
        require(snapshot.holdings.isNotEmpty()) { "Empty Vanguard holdings for $symbol" }
        run.fetched(symbol)
        changed = save(symbol, snapshot) || changed
        val updates = etfHoldingService.resolveVanguardUpdates(snapshot)
        industries += updates.industries
        countries += updates.countries
        changed = deleteNewerSnapshots(symbol, snapshot.effectiveDate) || changed
        run.persisted(symbol)
      }.onFailure { throwable ->
        run.failed(symbol, throwable)
        log.error("Vanguard holdings import failed for $symbol", throwable)
        failure = failure ?: throwable
        changed = fallBack(symbol) || changed
      }
    }
    runCatching { changed = etfHoldingIndustryService.updateVanguardIndustries(industries) > 0 || changed }
      .onFailure { throwable ->
        log.error("Vanguard industry reconciliation failed", throwable)
        failure = failure ?: throwable
      }
    if (failure == null) {
      runCatching { changed = etfHoldingCountryService.updateVanguardCountries(countries) > 0 || changed }
        .onFailure { throwable ->
          log.error("Vanguard country reconciliation failed", throwable)
          failure = throwable
        }
    }
    if (changed) {
      cacheInvalidationService.evictEtfBreakdownCache()
      cacheInvalidationService.evictDiversificationEtfsCache()
    }
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
    etfHoldingService.saveHoldings(symbol, snapshot.effectiveDate, snapshot.holdingsWithoutIndustries())
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
