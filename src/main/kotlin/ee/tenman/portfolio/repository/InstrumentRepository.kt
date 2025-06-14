package ee.tenman.portfolio.repository

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.domain.Instrument
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface InstrumentRepository : JpaRepository<Instrument, Long> {
  fun findBySymbol(symbol: String): Optional<Instrument>

  @Cacheable(value = [INSTRUMENT_CACHE], key = "'allInstruments'")
  override fun findAll(): List<Instrument>
}
