package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.EtfHolding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface EtfHoldingRepository : JpaRepository<EtfHolding, Long> {
  fun findByNameAndTicker(
    name: String,
    ticker: String?,
  ): Optional<EtfHolding>

  fun findByTicker(ticker: String): Optional<EtfHolding>

  fun findByName(name: String): Optional<EtfHolding>
}
