package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "portfolio_daily_summary")
class PortfolioDailySummary(
  @Column(name = "entry_date", nullable = false, unique = true)
  var entryDate: LocalDate,
  @Column(name = "total_value", nullable = false)
  var totalValue: BigDecimal,
  @Column(name = "xirr_annual_return", nullable = false)
  var xirrAnnualReturn: BigDecimal,
  @Column(name = "realized_profit", nullable = false)
  var realizedProfit: BigDecimal = BigDecimal.ZERO,
  @Column(name = "unrealized_profit", nullable = false)
  var unrealizedProfit: BigDecimal = BigDecimal.ZERO,
  @Column(name = "total_profit", nullable = false)
  var totalProfit: BigDecimal,
  @Column(name = "earnings_per_day", nullable = false)
  var earningsPerDay: BigDecimal,
) : BaseEntity()
