package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.model.holding.HoldingPositionRow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Repository
interface EtfPositionRepository : JpaRepository<EtfPosition, Long> {
  fun findByEtfInstrumentAndHoldingIdAndSnapshotDate(
    etfInstrument: Instrument,
    holdingId: Long,
    snapshotDate: LocalDate,
  ): EtfPosition?

  fun existsByEtfInstrumentSymbol(symbol: String): Boolean

  @Query("SELECT MAX(ep.snapshotDate) FROM EtfPosition ep WHERE ep.etfInstrument.symbol = :symbol")
  fun findLatestSnapshotDate(
    @Param("symbol") symbol: String,
  ): LocalDate?

  @Modifying
  @Transactional
  @Query(
    """
    DELETE FROM EtfPosition ep
    WHERE ep.etfInstrument.id IN (SELECT i.id FROM Instrument i WHERE i.symbol = :symbol)
    AND ep.snapshotDate > :snapshotDate
  """,
  )
  fun deleteBySymbolAndSnapshotDateAfter(
    @Param("symbol") symbol: String,
    @Param("snapshotDate") snapshotDate: LocalDate,
  ): Int

  @Modifying(flushAutomatically = true)
  @Query(
    """
    DELETE FROM EtfPosition ep
    WHERE ep.etfInstrument.id = :etfInstrumentId
    AND ep.snapshotDate = :snapshotDate
    AND ep.holding.id NOT IN :holdingIds
  """,
  )
  fun deleteMissingHoldings(
    @Param("etfInstrumentId") etfInstrumentId: Long,
    @Param("snapshotDate") snapshotDate: LocalDate,
    @Param("holdingIds") holdingIds: Set<Long>,
  ): Int

  fun findByHoldingId(holdingId: Long): List<EtfPosition>

  fun findByHoldingIdIn(holdingIds: List<Long>): List<EtfPosition>

  @Query(
    """
    SELECT COUNT(p) FROM EtfPosition p
    WHERE p.etfInstrument.id = :etfInstrumentId
    AND p.snapshotDate = :snapshotDate
  """,
  )
  fun countByEtfInstrumentIdAndDate(
    @Param("etfInstrumentId") etfInstrumentId: Long,
    @Param("snapshotDate") snapshotDate: LocalDate,
  ): Long

  @Query(
    """
    WITH latest AS MATERIALIZED (
      SELECT i.id AS etfInstrumentId,
        (SELECT MAX(ep2.snapshotDate) FROM EtfPosition ep2 WHERE ep2.etfInstrument.id = i.id) AS snapshotDate
      FROM Instrument i
      WHERE i.id IN :etfInstrumentIds
    )
    SELECT ep FROM EtfPosition ep
    JOIN FETCH ep.etfInstrument
    JOIN FETCH ep.holding
    JOIN latest l ON l.etfInstrumentId = ep.etfInstrument.id AND l.snapshotDate = ep.snapshotDate
    WHERE ep.etfInstrument.id IN :etfInstrumentIds
    ORDER BY ep.etfInstrument.id, ep.weightPercentage DESC
  """,
  )
  fun findLatestPositionsByEtfIds(
    @Param("etfInstrumentIds") etfInstrumentIds: List<Long>,
  ): List<EtfPosition>

  @Query(
    """
    WITH latest AS MATERIALIZED (
      SELECT i.id AS etfInstrumentId,
        (SELECT MAX(ep2.snapshotDate) FROM EtfPosition ep2 WHERE ep2.etfInstrument.id = i.id) AS snapshotDate
      FROM Instrument i
      WHERE i.id IN :etfInstrumentIds
    )
    SELECT new ee.tenman.portfolio.model.holding.HoldingPositionRow(
      ep.etfInstrument.id, ep.weightPercentage, h.uuid, h.ticker, h.name, h.sector, h.industry, h.countryCode, h.countryName
    )
    FROM EtfPosition ep
    JOIN ep.holding h
    JOIN latest l ON l.etfInstrumentId = ep.etfInstrument.id AND ep.snapshotDate >= l.snapshotDate
    WHERE ep.etfInstrument.id IN :etfInstrumentIds
  """,
  )
  fun findLatestHoldingRowsByEtfIds(
    @Param("etfInstrumentIds") etfInstrumentIds: List<Long>,
  ): List<HoldingPositionRow>

  @Query("SELECT DISTINCT ep.etfInstrument.id FROM EtfPosition ep")
  fun findDistinctEtfInstrumentIds(): List<Long>
}
