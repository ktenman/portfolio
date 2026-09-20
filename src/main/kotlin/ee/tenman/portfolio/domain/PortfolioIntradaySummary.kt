package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "portfolio_intraday_summary")
class PortfolioIntradaySummary(
  @Column(name = "captured_at", nullable = false, unique = true)
  val capturedAt: Instant,
  @Column(name = "total_value", nullable = false)
  val totalValue: BigDecimal,
  @Column(name = "xirr_annual_return", nullable = false)
  val xirrAnnualReturn: BigDecimal,
  @Column(name = "total_profit", nullable = false)
  val totalProfit: BigDecimal,
  @Column(name = "earnings_per_day", nullable = false)
  val earningsPerDay: BigDecimal,
) : BaseEntity()
