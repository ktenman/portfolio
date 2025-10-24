package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface InstrumentRepository : JpaRepository<Instrument, Long> {
  fun findBySymbol(symbol: String): Optional<Instrument>

  fun findBySymbolContaining(symbol: String): List<Instrument>

  fun findByProviderName(providerName: ProviderName): List<Instrument>
}
