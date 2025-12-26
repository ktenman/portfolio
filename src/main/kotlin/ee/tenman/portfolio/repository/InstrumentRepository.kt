package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.*

@Repository
interface InstrumentRepository : JpaRepository<Instrument, Long> {
  fun findBySymbol(symbol: String): Optional<Instrument>

  fun findBySymbolContaining(symbol: String): List<Instrument>

  fun findByProviderName(providerName: ProviderName): List<Instrument>

  @Modifying(clearAutomatically = true)
  @Query("UPDATE Instrument i SET i.currentPrice = :price WHERE i.id = :id")
  fun updateCurrentPrice(
    id: Long,
    price: BigDecimal?,
  )

  @Modifying(clearAutomatically = true)
  @Query("UPDATE Instrument i SET i.providerExternalId = :providerExternalId WHERE i.symbol = :symbol")
  fun updateProviderExternalId(
    symbol: String,
    providerExternalId: String,
  )

  @Modifying(clearAutomatically = true)
  @Query("UPDATE Instrument i SET i.ter = :ter WHERE i.id = :id")
  fun updateTer(
    id: Long,
    ter: BigDecimal?,
  )

  @Modifying(clearAutomatically = true)
  @Query("UPDATE Instrument i SET i.xirrAnnualReturn = :xirr WHERE i.id = :id")
  fun updateXirrAnnualReturn(
    id: Long,
    xirr: BigDecimal?,
  )
}
