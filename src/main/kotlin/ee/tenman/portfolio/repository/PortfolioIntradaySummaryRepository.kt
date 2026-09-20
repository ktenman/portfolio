package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.PortfolioIntradaySummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

@Repository
interface PortfolioIntradaySummaryRepository : JpaRepository<PortfolioIntradaySummary, Long> {
  @Modifying
  @Query(
    """
    INSERT INTO portfolio_intraday_summary
      (captured_at, total_value, xirr_annual_return, total_profit, earnings_per_day, created_at, updated_at, version)
    VALUES (:capturedAt, :totalValue, :xirrAnnualReturn, :totalProfit, :earningsPerDay, NOW(), NOW(), 0)
    ON CONFLICT (captured_at)
    DO UPDATE SET
      total_value = :totalValue,
      xirr_annual_return = :xirrAnnualReturn,
      total_profit = :totalProfit,
      earnings_per_day = :earningsPerDay,
      updated_at = NOW(),
      version = portfolio_intraday_summary.version + 1
    """,
    nativeQuery = true,
  )
  fun upsert(
    capturedAt: Instant,
    totalValue: BigDecimal,
    xirrAnnualReturn: BigDecimal,
    totalProfit: BigDecimal,
    earningsPerDay: BigDecimal,
  )

  @Query(
    """
    SELECT DISTINCT ON (bucket) s.*, date_bin(:bucketSeconds * INTERVAL '1 second', s.captured_at, TIMESTAMPTZ 'epoch') AS bucket
    FROM portfolio_intraday_summary s
    WHERE s.captured_at >= :from
    ORDER BY bucket, s.captured_at DESC
    """,
    nativeQuery = true,
  )
  fun findBucketed(
    from: Instant,
    bucketSeconds: Long,
  ): List<PortfolioIntradaySummary>

  @Modifying
  @Query("DELETE FROM portfolio_intraday_summary WHERE captured_at < :cutoff", nativeQuery = true)
  fun deleteOlderThan(cutoff: Instant)
}
