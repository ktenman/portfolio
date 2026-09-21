package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.InstrumentMinutePrice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface InstrumentMinutePriceRepository : JpaRepository<InstrumentMinutePrice, Long> {
  @Modifying
  @Query(
    """
    INSERT INTO instrument_minute_price (instrument_id, captured_at, price)
    SELECT i.id, :capturedAt, i.current_price
    FROM instrument i
    LEFT JOIN LATERAL (
      SELECT p.price, p.captured_at
      FROM instrument_minute_price p
      WHERE p.instrument_id = i.id
      ORDER BY p.captured_at DESC
      LIMIT 1
    ) last ON TRUE
    WHERE i.current_price > 0
      AND (
        last.price IS DISTINCT FROM i.current_price
        OR last.captured_at < CAST(:capturedAt AS TIMESTAMP WITH TIME ZONE) - INTERVAL '1 day'
      )
    ON CONFLICT DO NOTHING
    """,
    nativeQuery = true,
  )
  fun insertChangedPrices(capturedAt: Instant)

  @Query(
    """
    SELECT last.*
    FROM instrument i
    CROSS JOIN LATERAL (
      SELECT p.*
      FROM instrument_minute_price p
      WHERE p.instrument_id = i.id
        AND p.captured_at <= :from
      ORDER BY p.captured_at DESC
      LIMIT 1
    ) last
    WHERE i.id IN (:instrumentIds)
    UNION ALL
    SELECT p.*
    FROM instrument_minute_price p
    WHERE p.instrument_id IN (:instrumentIds)
      AND p.captured_at > :from
      AND p.captured_at <= :until
    ORDER BY captured_at ASC
    """,
    nativeQuery = true,
  )
  fun findForReplay(
    instrumentIds: List<Long>,
    from: Instant,
    until: Instant,
  ): List<InstrumentMinutePrice>

  @Modifying
  @Query("DELETE FROM instrument_minute_price WHERE captured_at < :cutoff", nativeQuery = true)
  fun deleteOlderThan(cutoff: Instant)
}
