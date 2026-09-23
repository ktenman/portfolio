package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
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
    SELECT ep FROM EtfPosition ep
    JOIN FETCH ep.etfInstrument
    JOIN FETCH ep.holding
    WHERE ep.etfInstrument.id = :etfInstrumentId
    AND ep.snapshotDate = (
      SELECT MAX(ep2.snapshotDate)
      FROM EtfPosition ep2
      WHERE ep2.etfInstrument.id = :etfInstrumentId
    )
    ORDER BY ep.weightPercentage DESC
  """,
  )
  fun findLatestPositionsByEtfId(
    @Param("etfInstrumentId") etfInstrumentId: Long,
  ): List<EtfPosition>

  @Query(
    """
    WITH latest AS MATERIALIZED (
      SELECT ep2.etfInstrument.id AS etfInstrumentId, MAX(ep2.snapshotDate) AS snapshotDate
      FROM EtfPosition ep2
      WHERE ep2.etfInstrument.id IN :etfInstrumentIds
      GROUP BY ep2.etfInstrument.id
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

  @Query("SELECT DISTINCT ep.etfInstrument.id FROM EtfPosition ep")
  fun findDistinctEtfInstrumentIds(): List<Long>
}
