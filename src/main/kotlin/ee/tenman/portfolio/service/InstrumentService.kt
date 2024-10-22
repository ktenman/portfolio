package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE_5
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.InstrumentRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class InstrumentService(
  private val instrumentRepository: InstrumentRepository,
  private val dailyPriceService: DailyPriceService
) {

  @Transactional(readOnly = true)
  @Cacheable(value = [INSTRUMENT_CACHE], key = "#id")
  fun getInstrumentById(id: Long): Instrument = instrumentRepository.findById(id)
    .orElseThrow { RuntimeException("Instrument not found with id: $id") }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [INSTRUMENT_CACHE], key = "#instrument.id", condition = "#instrument.id != null"),
      CacheEvict(value = [INSTRUMENT_CACHE], key = "#instrument.symbol"),
      CacheEvict(value = [INSTRUMENT_CACHE], key = "'allInstruments'")
    ]
  )
  fun saveInstrument(instrument: Instrument): Instrument = instrumentRepository.save(instrument)

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [INSTRUMENT_CACHE], key = "#id"),
      CacheEvict(value = [INSTRUMENT_CACHE], key = "'allInstruments'")
    ]
  )
  fun deleteInstrument(id: Long) = instrumentRepository.deleteById(id)

  @Transactional(readOnly = true)
  @Cacheable(value = [INSTRUMENT_CACHE], key = "'allInstruments'", unless = "#result.isEmpty()")
  fun getAllInstruments(): List<Instrument> {
    return instrumentRepository.findAll()
  }

  @Cacheable(value = [INSTRUMENT_CACHE_5], key = "'getLatestPrices'", unless = "#result.isEmpty()")
  fun getLatestPrices(): Map<String, BigDecimal> {
    val instruments = instrumentRepository.findAll()
    return instruments.map { instrument ->
      val dailyPrice = dailyPriceService.findLastDailyPrice(instrument, LocalDate.now())
      Pair(instrument.symbol, instrument.currentPrice ?: (dailyPrice?.closePrice ?: BigDecimal.ZERO))
    }.toMap()
  }

  fun findInstrument(id: Long): Instrument {
    return getInstrumentById(id)
  }

  @Cacheable(value = [INSTRUMENT_CACHE], key = "#symbol", unless = "#result == null")
  fun getInstrument(symbol: String): Instrument {
    return instrumentRepository.findBySymbol(symbol)
      .orElseThrow { RuntimeException("Instrument not found with symbol: $symbol") }
  }

}
