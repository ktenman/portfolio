package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.PendingHoldingImport
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.dto.requireImportable
import ee.tenman.portfolio.repository.PendingHoldingImportRepository
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

@Service
class HoldingImportService(
  private val pendingHoldingImportRepository: PendingHoldingImportRepository,
  private val etfHoldingService: EtfHoldingService,
  private val cacheInvalidationService: CacheInvalidationService,
  private val objectMapper: ObjectMapper,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun queue(
    etfSymbol: String,
    date: LocalDate,
    holdings: List<HoldingData>,
  ) {
    holdings.requireImportable(etfSymbol, date)
    val snapshot = pendingHoldingImportRepository.save(PendingHoldingImport(etfSymbol, date, objectMapper.writeValueAsString(holdings)))
    log.info("Queued ${holdings.size} holdings for ETF $etfSymbol on $date as snapshot ${snapshot.id}")
  }

  fun hasPending(): Boolean = pendingHoldingImportRepository.count() > 0

  fun importPending() {
    val failures = pendingHoldingImportRepository.findLatestPerSymbol().mapNotNull(::attempt)
    val failure = failures.firstOrNull() ?: return
    failures.drop(1).forEach(failure::addSuppressed)
    throw failure
  }

  private fun attempt(snapshot: PendingHoldingImport): Throwable? =
    runCatching { publish(snapshot) }
      .exceptionOrNull()
      ?.let { IllegalStateException("Failed to import holdings for ETF ${snapshot.etfSymbol} on ${snapshot.snapshotDate}", it) }

  private fun publish(snapshot: PendingHoldingImport) {
    val holdings: List<HoldingData> = objectMapper.readValue(snapshot.holdings)
    etfHoldingService.saveHoldings(snapshot.etfSymbol, snapshot.snapshotDate, holdings)
    cacheInvalidationService.evictEtfBreakdownCache()
    pendingHoldingImportRepository.deleteByEtfSymbolAndIdLessThanEqual(snapshot.etfSymbol, snapshot.id)
  }
}
