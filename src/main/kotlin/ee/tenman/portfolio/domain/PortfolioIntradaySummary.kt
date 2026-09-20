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
  var capturedAt: Instant,
  @Column(name = "total_value", nullable = false)
  var totalValue: BigDecimal,
  @Column(name = "xirr_annual_return", nullable = false)
  var xirrAnnualReturn: BigDecimal,
  @Column(name = "total_profit", nullable = false)
  var totalProfit: BigDecimal,
  @Column(name = "earnings_per_day", nullable = false)
  var earningsPerDay: BigDecimal,
) : BaseEntity()
