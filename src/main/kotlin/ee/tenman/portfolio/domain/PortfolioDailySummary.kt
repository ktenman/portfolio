package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
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
) : BaseEntity() {
  private fun writeObject(output: ObjectOutputStream) {
    output.writeLong(entryDate.toEpochDay())
    listOf(totalValue, xirrAnnualReturn, realizedProfit, unrealizedProfit, totalProfit, earningsPerDay).forEach { output.writeDecimal(it) }
  }

  private fun readObject(input: ObjectInputStream) {
    entryDate = LocalDate.ofEpochDay(input.readLong())
    totalValue = input.readDecimal()
    xirrAnnualReturn = input.readDecimal()
    realizedProfit = input.readDecimal()
    unrealizedProfit = input.readDecimal()
    totalProfit = input.readDecimal()
    earningsPerDay = input.readDecimal()
  }

  companion object {
    private const val serialVersionUID: Long = 3196017946321380390L
  }
}
