package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.exception.EntityNotFoundException
import ee.tenman.portfolio.model.InstrumentSnapshot
import ee.tenman.portfolio.repository.InstrumentRepository
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
  fun getInstrumentById(id: Long): Instrument =
    instrumentRepository.findById(id).orElseThrow {
      EntityNotFoundException("Instrument not found with id: $id")
    }

  @Transactional(readOnly = true)
  fun findBySymbol(symbol: String): Instrument =
    instrumentRepository.findBySymbol(symbol).orElseThrow {
      EntityNotFoundException("Instrument not found with symbol: $symbol")
    }

  @Transactional
  fun saveInstrument(instrument: Instrument): Instrument {
    val saved = instrumentRepository.save(instrument)
    transactionProfitService.recalculateProfitsForInstrument(saved.id)
    cacheInvalidationService.evictAllRelatedCaches(saved.id, saved.symbol)
    return saved
  }

  @Transactional
  fun deleteInstrument(id: Long) {
    val instrument = instrumentRepository.findById(id).orElse(null)
    instrumentRepository.deleteById(id)
    cacheInvalidationService.evictInstrumentCaches(id, instrument?.symbol)
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

  @Transactional
  fun updateCurrentPrice(
    instrumentId: Long,
    price: BigDecimal?,
  ) {
    instrumentRepository.updateCurrentPrice(instrumentId, price)
    val instrument = instrumentRepository.findById(instrumentId).orElse(null)
    if (instrument != null) {
      transactionProfitService.recalculateProfitsForInstrument(instrumentId)
      cacheInvalidationService.evictAllRelatedCaches(instrumentId, instrument.symbol)
    } else {
      cacheInvalidationService.evictInstrumentCaches(instrumentId, null)
    }
  }
}
