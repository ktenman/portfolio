package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.EtfHolding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EtfHoldingRepository : JpaRepository<EtfHolding, Long> {
  fun findByUuid(uuid: UUID): EtfHolding?

  @Query("SELECT h FROM EtfHolding h WHERE LOWER(h.name) = LOWER(:name) ORDER BY h.id ASC")
  fun findByNameIgnoreCase(
    @Param("name") name: String,
  ): EtfHolding?

  fun findByNameBlockKey(nameBlockKey: String): List<EtfHolding>

  @Query(
    """
    SELECT h.nameBlockKey FROM EtfHolding h
    WHERE h.nameBlockKey IS NOT NULL AND h.nameBlockKey <> ''
    GROUP BY h.nameBlockKey
    HAVING COUNT(h.id) > 1
  """,
  )
  fun findDuplicateBlockKeys(): List<String>

  fun findByTicker(ticker: String): List<EtfHolding>

  @Query(
    """
    SELECT h FROM EtfHolding h
    JOIN EtfPosition ep ON ep.holding.id = h.id
    WHERE h.sectorSource IS NULL OR h.sectorSource <> ee.tenman.portfolio.domain.SectorSource.LLM
    GROUP BY h.id
    ORDER BY MAX(ep.weightPercentage) DESC
  """,
  )
  fun findUnclassifiedSectorHoldings(): List<EtfHolding>

  @Query(
    """
    SELECT h FROM EtfHolding h
    JOIN EtfPosition ep ON ep.holding.id = h.id
    WHERE (h.countryCode IS NULL OR h.countryCode = '')
      AND h.countryFetchAttempts < :maxAttempts
    GROUP BY h.id
    ORDER BY MAX(ep.weightPercentage) DESC
  """,
  )
  fun findUnclassifiedCountryHoldings(
    @Param("maxAttempts") maxAttempts: Int = 3,
  ): List<EtfHolding>

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

  @Query(
    """
    SELECT h FROM EtfHolding h
    JOIN EtfPosition ep ON ep.holding.id = h.id
    JOIN PortfolioTransaction pt ON pt.instrument.id = ep.etfInstrument.id
    WHERE h.logoSource IS NULL
    GROUP BY h.id
    HAVING SUM(CASE WHEN pt.transactionType = ee.tenman.portfolio.domain.TransactionType.BUY THEN pt.quantity ELSE -pt.quantity END) > 0.01
    ORDER BY MAX(ep.weightPercentage) DESC
  """,
  )
  fun findHoldingsWithoutLogosForCurrentPortfolio(): List<EtfHolding>
}
