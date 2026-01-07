package ee.tenman.portfolio.service.instrument

import ee.tenman.portfolio.common.orThrow
import ee.tenman.portfolio.common.orThrowByField
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.model.InstrumentSnapshot
import ee.tenman.portfolio.model.InstrumentSnapshotsWithPortfolioXirr
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.CacheInvalidationService
import ee.tenman.portfolio.service.transaction.TransactionProfitService
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class InstrumentService(
  private val instrumentRepository: InstrumentRepository,
  private val transactionProfitService: TransactionProfitService,
  private val instrumentSnapshotService: InstrumentSnapshotService,
  private val cacheInvalidationService: CacheInvalidationService,
) {
  @Transactional(readOnly = true)
  @Cacheable(value = [INSTRUMENT_CACHE], key = "#id")
  fun getInstrumentById(id: Long): Instrument = instrumentRepository.findById(id).orThrow(id)

  @Transactional(readOnly = true)
  fun findBySymbol(symbol: String): Instrument = instrumentRepository.findBySymbol(symbol).orThrowByField("symbol", symbol)

  @Transactional
  fun saveInstrument(instrument: Instrument): Instrument {
    val saved = instrumentRepository.save(instrument)
    transactionProfitService.recalculateProfitsForInstrument(saved.id)
    cacheInvalidationService.evictAllRelatedCaches(saved.id, saved.symbol)
    return saved
  }

  @Transactional
  fun deleteInstrument(id: Long) {
    val symbol = instrumentRepository.findById(id).map { it.symbol }.orElse(null)
    instrumentRepository.deleteById(id)
    cacheInvalidationService.evictInstrumentCaches(id, symbol)
  }

  @Transactional(readOnly = true)
  fun getAllInstrumentsWithoutFiltering(): List<Instrument> = instrumentRepository.findAll()

  @Transactional(readOnly = true)
  fun getAllInstrumentSnapshots(): List<InstrumentSnapshot> = instrumentSnapshotService.getAllSnapshots()

  @Transactional(readOnly = true)
  fun getAllInstrumentSnapshots(platforms: List<String>?): List<InstrumentSnapshot> = instrumentSnapshotService.getAllSnapshots(platforms)

  @Transactional(readOnly = true)
  fun getAllInstrumentSnapshots(
    platforms: List<String>?,
    period: String?,
  ): List<InstrumentSnapshot> = instrumentSnapshotService.getAllSnapshots(platforms, period)

  @Transactional(readOnly = true)
  fun getAllInstrumentSnapshotsWithPortfolioXirr(
    platforms: List<String>?,
    period: String?,
  ): InstrumentSnapshotsWithPortfolioXirr = instrumentSnapshotService.getAllSnapshotsWithPortfolioXirr(platforms, period)

  @Transactional
  fun updateCurrentPrice(
    instrumentId: Long,
    price: BigDecimal?,
  ) {
    instrumentRepository.updateCurrentPrice(instrumentId, price)
    val symbol =
      instrumentRepository.findById(instrumentId).map { it.symbol }.orElse(null)
      ?: return cacheInvalidationService.evictInstrumentCaches(instrumentId, null)
    transactionProfitService.recalculateProfitsForInstrument(instrumentId)
    cacheInvalidationService.evictAllRelatedCaches(instrumentId, symbol)
  }

  @Transactional
  fun updateProviderExternalId(
    symbol: String,
    providerExternalId: String,
  ) {
    instrumentRepository.updateProviderExternalId(symbol, providerExternalId)
  }

  @Transactional
  fun updateTer(
    instrumentId: Long,
    ter: BigDecimal?,
  ) {
    instrumentRepository.updateTer(instrumentId, ter)
  }

  @Transactional
  fun updateXirrAnnualReturn(
    instrumentId: Long,
    xirr: BigDecimal?,
  ) {
    instrumentRepository.updateXirrAnnualReturn(instrumentId, xirr)
  }
}
