package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.PriceSnapshot
import ee.tenman.portfolio.domain.ProviderName
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
    SELECT EXISTS(
      SELECT 1 FROM price_snapshot
      WHERE instrument_id = :instrumentId
        AND provider_name = :providerName
    )
    """,
    nativeQuery = true,
  )
  fun existsByInstrumentIdAndProviderName(
    instrumentId: Long,
    providerName: String,
  ): Boolean

  @Query(
    """
    SELECT ps FROM PriceSnapshot ps
    WHERE ps.instrument.id = :instrumentId
      AND ps.providerName = :providerName
      AND ps.snapshotHour BETWEEN :earliestHour AND :targetHour
    ORDER BY ps.snapshotHour DESC
    LIMIT 1
    """,
  )
  fun findClosestAtOrBefore(
    instrumentId: Long,
    providerName: ProviderName,
    earliestHour: Instant,
    targetHour: Instant,
  ): PriceSnapshot?

  @Modifying
  @Query(
    """
    INSERT INTO price_snapshot (instrument_id, provider_name, snapshot_hour, price, created_at, updated_at, version)
    SELECT * FROM UNNEST(
      :instrumentIds,
      :providerNames,
      :snapshotHours,
      :prices,
      ARRAY_FILL(NOW()::timestamptz, ARRAY[:size]),
      ARRAY_FILL(NOW()::timestamptz, ARRAY[:size]),
      ARRAY_FILL(0::bigint, ARRAY[:size])
    ) AS t(instrument_id, provider_name, snapshot_hour, price, created_at, updated_at, version)
    ON CONFLICT (instrument_id, provider_name, snapshot_hour)
    DO UPDATE SET price = EXCLUDED.price, updated_at = NOW(), version = price_snapshot.version + 1
    """,
    nativeQuery = true,
  )
  fun upsertBatch(
    instrumentIds: Array<Long>,
    providerNames: Array<String>,
    snapshotHours: Array<Instant>,
    prices: Array<BigDecimal>,
    size: Int,
  )

  @Modifying(clearAutomatically = true)
  @Query(
    """
    DELETE FROM price_snapshot
    WHERE id IN (
      SELECT id FROM price_snapshot
      WHERE snapshot_hour < :cutoff
      LIMIT :batchSize
    )
    """,
    nativeQuery = true,
  )
  fun deleteBatchOlderThan(
    cutoff: Instant,
    batchSize: Int,
  ): Int
}
