package ee.tenman.portfolio.service.xirr

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class TransactionCalculator(private val baseMonthlyInvestment: BigDecimal) {
  private val transactions: MutableList<Transaction> = ArrayList()
  private var totalBoughtStocksCount: BigDecimal = BigDecimal.ZERO
  private var lastMonthDate: LocalDate? = null

  fun getTransactions(): List<Transaction> {
    return transactions
  }

  fun processDate(date: LocalDate, price: BigDecimal, lastDataDate: LocalDate?) {
    if (this.shouldAddStockPurchase(date)) {
      this.addStockPurchaseTransaction(date, price)
    }
    if (date.isEqual(lastDataDate)) {
      this.addFinalSellingTransaction(date, price)
    }
  }

  private fun shouldAddStockPurchase(date: LocalDate): Boolean {
    return this.lastMonthDate == null || ChronoUnit.MONTHS.between(
      lastMonthDate!!.withDayOfMonth(1),
      date.withDayOfMonth(1)
    ) >= 1
  }

  private fun addStockPurchaseTransaction(date: LocalDate, price: BigDecimal) {
    this.lastMonthDate = date
    val stocksCount = baseMonthlyInvestment.divide(price, RoundingMode.DOWN)
    this.totalBoughtStocksCount = totalBoughtStocksCount.add(stocksCount)
    transactions.add(Transaction(baseMonthlyInvestment.negate().toDouble(), date))
  }

  private fun addFinalSellingTransaction(date: LocalDate, price: BigDecimal) {
    val amount = price.multiply(this.totalBoughtStocksCount)
    transactions.add(Transaction(amount.toDouble(), date))
  }
}
