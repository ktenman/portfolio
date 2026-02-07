package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.PriceSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

@Repository
interface PriceSnapshotRepository : JpaRepository<PriceSnapshot, Long> {
  @Modifying
  @Query(
    """
    INSERT INTO price_snapshot (instrument_id, provider_name, snapshot_hour, price, created_at, updated_at, version)
    VALUES (:instrumentId, :providerName, :snapshotHour, :price, NOW(), NOW(), 0)
    ON CONFLICT (instrument_id, provider_name, snapshot_hour)
    DO UPDATE SET price = :price, updated_at = NOW(), version = price_snapshot.version + 1
    """,
    nativeQuery = true,
  )
  fun upsert(
    instrumentId: Long,
    providerName: String,
    snapshotHour: Instant,
    price: BigDecimal,
  )

  @Query(
    """
    SELECT ps FROM PriceSnapshot ps
    WHERE ps.instrument.id = :instrumentId
      AND ps.providerName = :providerName
    ORDER BY ps.snapshotHour DESC
    LIMIT 1
    """,
  )
  fun findLatestByInstrumentAndProvider(
    instrumentId: Long,
    providerName: String,
  ): PriceSnapshot?

  @Query(
    """
    SELECT ps FROM PriceSnapshot ps
    WHERE ps.instrument.id = :instrumentId
      AND ps.snapshotHour <= :targetHour
    ORDER BY ps.snapshotHour DESC
    LIMIT 1
    """,
  )
  fun findClosestBefore(
    instrumentId: Long,
    targetHour: Instant,
  ): PriceSnapshot?

  @Modifying
  @Query("DELETE FROM PriceSnapshot ps WHERE ps.snapshotHour < :cutoff")
  fun deleteOlderThan(cutoff: Instant)
}
