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

  @Query("SELECT h FROM EtfHolding h WHERE LOWER(h.name) = LOWER(:name) ORDER BY h.id ASC")
  fun findByNameIgnoreCase(
    @Param("name") name: String,
  ): Optional<EtfHolding>

  fun findFirstByTickerOrderByIdDesc(ticker: String): Optional<EtfHolding>

  fun findByName(name: String): Optional<EtfHolding>

  fun findBySectorIsNullOrSectorEquals(sector: String): List<EtfHolding>

  fun findByCountryCodeIsNullOrCountryCodeEquals(countryCode: String): List<EtfHolding>

  @Query(
    """
    SELECT h FROM EtfHolding h
    JOIN EtfPosition ep ON ep.holding.id = h.id
    JOIN PortfolioTransaction pt ON pt.instrument.id = ep.etfInstrument.id
    WHERE (h.sector IS NULL OR h.sector = '')
    GROUP BY h.id
    HAVING SUM(CASE WHEN pt.transactionType = ee.tenman.portfolio.domain.TransactionType.BUY THEN pt.quantity ELSE -pt.quantity END) > 0.01
    ORDER BY MAX(ep.weightPercentage) DESC
  """,
  )
  fun findUnclassifiedSectorHoldingsForCurrentPortfolio(): List<EtfHolding>

  @Query(
    """
    SELECT h FROM EtfHolding h
    JOIN EtfPosition ep ON ep.holding.id = h.id
    JOIN PortfolioTransaction pt ON pt.instrument.id = ep.etfInstrument.id
    WHERE (h.countryCode IS NULL OR h.countryCode = '')
    GROUP BY h.id
    HAVING SUM(CASE WHEN pt.transactionType = ee.tenman.portfolio.domain.TransactionType.BUY THEN pt.quantity ELSE -pt.quantity END) > 0.01
    ORDER BY MAX(ep.weightPercentage) DESC
  """,
  )
  fun findUnclassifiedCountryHoldingsForCurrentPortfolio(): List<EtfHolding>

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
    WHERE h.logoFetched = false
    GROUP BY h.id
    HAVING SUM(CASE WHEN pt.transactionType = ee.tenman.portfolio.domain.TransactionType.BUY THEN pt.quantity ELSE -pt.quantity END) > 0.01
    ORDER BY MAX(ep.weightPercentage) DESC
  """,
  )
  fun findHoldingsWithoutLogosForCurrentPortfolio(): List<EtfHolding>
}
