package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface EtfPositionRepository : JpaRepository<EtfPosition, Long> {
  fun findByEtfInstrumentAndHoldingIdAndSnapshotDate(
    etfInstrument: Instrument,
    holdingId: Long,
    snapshotDate: LocalDate,
  ): EtfPosition?

  @Query(
    """
    SELECT p FROM EtfPosition p
    WHERE p.etfInstrument.symbol = :etfSymbol
    AND p.snapshotDate = :snapshotDate
    ORDER BY p.positionRank
  """,
  )
  fun findByEtfSymbolAndDate(
    @Param("etfSymbol") etfSymbol: String,
    @Param("snapshotDate") snapshotDate: LocalDate,
  ): List<EtfPosition>

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
    SELECT ep FROM EtfPosition ep
    JOIN FETCH ep.etfInstrument
    JOIN FETCH ep.holding
    WHERE ep.etfInstrument.id IN :etfInstrumentIds
    AND (ep.etfInstrument.id, ep.snapshotDate) IN (
      SELECT ep2.etfInstrument.id, MAX(ep2.snapshotDate)
      FROM EtfPosition ep2
      WHERE ep2.etfInstrument.id IN :etfInstrumentIds
      GROUP BY ep2.etfInstrument.id
    )
    ORDER BY ep.etfInstrument.id, ep.weightPercentage DESC
  """,
  )
  fun findLatestPositionsByEtfIds(
    @Param("etfInstrumentIds") etfInstrumentIds: List<Long>,
  ): List<EtfPosition>

  @Query(
    """
    SELECT ep FROM EtfPosition ep
    WHERE ep.etfInstrument.id = :etfInstrumentId
    AND ep.snapshotDate = :snapshotDate
  """,
  )
  fun findByEtfInstrumentIdAndSnapshotDate(
    @Param("etfInstrumentId") etfInstrumentId: Long,
    @Param("snapshotDate") snapshotDate: LocalDate,
  ): List<EtfPosition>
}
