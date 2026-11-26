package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.DailyPricePoint
import ee.tenman.portfolio.domain.MinutePricePoint
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

interface MinutePricePointQueries {
  fun findPriceChangesAt(
    instrumentIds: List<Long>,
    times: List<Instant>,
  ): List<MinutePricePoint>
}

class MinutePricePointQueriesImpl(
  private val jdbcClient: JdbcClient,
) : MinutePricePointQueries {
  override fun findPriceChangesAt(
    instrumentIds: List<Long>,
    times: List<Instant>,
  ): List<MinutePricePoint> =
    jdbcClient
      .sql(
        """
        SELECT t.n, i.id, latest.price
        FROM (
          SELECT u.at, u.n, lag(u.at, 1, '-infinity') OVER (ORDER BY u.at) AS since
          FROM unnest(ARRAY[:times]::timestamptz[]) WITH ORDINALITY AS u(at, n)
        ) t
        CROSS JOIN unnest(ARRAY[:instrumentIds]::bigint[]) AS i(id)
        JOIN LATERAL (
          SELECT p.price
          FROM instrument_minute_price p
          WHERE p.instrument_id = i.id AND p.captured_at <= t.at AND p.captured_at > t.since
          ORDER BY p.captured_at DESC
          LIMIT 1
        ) latest ON true
        """,
      ).param("times", times.map { it.atOffset(ZoneOffset.UTC) })
      .param("instrumentIds", instrumentIds)
      .query { row, _ -> MinutePricePoint(row.getLong(2), times[row.getInt(1) - 1], row.getBigDecimal(3)) }
      .list()
}

interface DailyPricePointQueries {
  fun findPricePointsSince(
    held: Map<Long, ClosedRange<LocalDate>>,
    from: LocalDate,
  ): List<DailyPricePoint>

  fun findLatestPricePointsOnDates(
    instrumentIds: Collection<Long>,
    dates: Collection<LocalDate>,
  ): List<DailyPricePoint>
}

private val dailyPricePoint =
  RowMapper { row, _ -> DailyPricePoint(row.getLong(1), row.getObject(2, LocalDate::class.java), row.getBigDecimal(3)) }

class DailyPricePointQueriesImpl(
  private val jdbcClient: JdbcClient,
) : DailyPricePointQueries {
  override fun findPricePointsSince(
    held: Map<Long, ClosedRange<LocalDate>>,
    from: LocalDate,
  ): List<DailyPricePoint> =
    jdbcClient
      .sql(
        """
        (
          SELECT d.instrument_id, d.entry_date, d.close_price
          FROM unnest(ARRAY[:instrumentIds]::bigint[], ARRAY[:heldSince]::date[], ARRAY[:heldUntil]::date[]) AS s(id, held, until)
          JOIN LATERAL (
            SELECT d.instrument_id, d.entry_date, d.close_price, d.ctid
            FROM daily_price d
            WHERE d.instrument_id = s.id AND d.entry_date <= s.until AND d.entry_date >= greatest(:from::date, coalesce(
              (SELECT max(p.entry_date) FROM daily_price p WHERE p.instrument_id = s.id AND p.entry_date <= s.held),
              s.held
            ))
            ORDER BY d.ctid
          ) d ON true
        )
        UNION ALL
        (
          SELECT p.instrument_id, p.entry_date, p.close_price
          FROM unnest(ARRAY[:instrumentIds]::bigint[]) AS i(id)
          JOIN LATERAL (
            SELECT entry_date FROM daily_price
            WHERE instrument_id = i.id AND entry_date < :from
            ORDER BY entry_date DESC
            LIMIT 1
          ) seed ON true
          JOIN daily_price p ON p.instrument_id = i.id AND p.entry_date = seed.entry_date
        )
        """,
      ).param("instrumentIds", held.keys.toList())
      .param("heldSince", held.values.map { it.start })
      .param("heldUntil", held.values.map { it.endInclusive })
      .param("from", from)
      .query(dailyPricePoint)
      .list()

  override fun findLatestPricePointsOnDates(
    instrumentIds: Collection<Long>,
    dates: Collection<LocalDate>,
  ): List<DailyPricePoint> =
    jdbcClient
      .sql(
        """
        SELECT i.id, latest.entry_date, latest.close_price
        FROM unnest(ARRAY[:instrumentIds]::bigint[]) AS i(id)
        CROSS JOIN unnest(ARRAY[:dates]::date[]) AS d(day)
        JOIN LATERAL (
          SELECT dp.entry_date, dp.close_price
          FROM daily_price dp
          WHERE dp.instrument_id = i.id AND dp.entry_date <= d.day
          ORDER BY dp.entry_date DESC, dp.provider_name DESC
          LIMIT 1
        ) latest ON true
        """,
      ).param("instrumentIds", instrumentIds)
      .param("dates", dates)
      .query(dailyPricePoint)
      .list()
}
