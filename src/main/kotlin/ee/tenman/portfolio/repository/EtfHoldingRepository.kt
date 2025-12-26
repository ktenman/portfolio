package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.EtfHolding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

  fun findBySectorIsNullOrSectorEquals(sector: String): List<EtfHolding>

  fun findByCountryCodeIsNullOrCountryCodeEquals(countryCode: String): List<EtfHolding>

  @Query(
    """
    SELECT DISTINCT ep.etfInstrument.name
    FROM EtfPosition ep
    WHERE ep.holding.id = :holdingId
  """,
  )
  fun findEtfNamesForHolding(
    @Param("holdingId") holdingId: Long,
  ): List<String>

  @Query(
    """
    SELECT ep.holding.id, ep.etfInstrument.name
    FROM EtfPosition ep
    WHERE ep.holding.id IN :holdingIds
  """,
  )
  fun findEtfNamesForHoldings(
    @Param("holdingIds") holdingIds: List<Long>,
  ): List<Array<Any>>
}
