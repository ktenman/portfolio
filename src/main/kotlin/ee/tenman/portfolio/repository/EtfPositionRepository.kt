package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.EtfPositionId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface EtfPositionRepository : JpaRepository<EtfPosition, EtfPositionId> {
  @Modifying
  @Query(
    nativeQuery = true,
    value = """
      INSERT INTO etf_positions (
        etf_instrument_id, holding_id, snapshot_date, weight_percentage,
        position_rank, market_cap, price, day_change
      )
      VALUES (
        :etfInstrumentId, :holdingId, :snapshotDate, :weightPercentage,
        :positionRank, :marketCap, :price, :dayChange
      )
      ON CONFLICT (etf_instrument_id, holding_id, snapshot_date)
      DO UPDATE SET
        weight_percentage = EXCLUDED.weight_percentage,
        position_rank = EXCLUDED.position_rank,
        market_cap = EXCLUDED.market_cap,
        price = EXCLUDED.price,
        day_change = EXCLUDED.day_change
    """,
  )
  fun upsertPosition(
    @Param("etfInstrumentId") etfInstrumentId: Long,
    @Param("holdingId") holdingId: Long,
    @Param("snapshotDate") snapshotDate: LocalDate,
    @Param("weightPercentage") weightPercentage: BigDecimal,
    @Param("positionRank") positionRank: Int?,
    @Param("marketCap") marketCap: String?,
    @Param("price") price: String?,
    @Param("dayChange") dayChange: String?,
  )

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
}
