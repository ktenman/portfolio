package ee.tenman.portfolio.service.xirr

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

internal class XirrDetails(transactions: Collection<Transaction>?) {
  var start: LocalDate? = null
  var end: LocalDate? = null
  var minAmount: Double = Double.POSITIVE_INFINITY
  var maxAmount: Double = Double.NEGATIVE_INFINITY
  var total: Double = 0.0
  var deposits: Double = 0.0

  init {
    require(!transactions.isNullOrEmpty()) { "Transactions collection cannot be null or empty." }
    transactions.forEach { transaction -> this.processTransaction(transaction) }
    this.validateDateRange()
    this.validateTransactionAmounts()
  }

  private fun processTransaction(transaction: Transaction) {
    this.updateDateRange(transaction)
    this.updateAmounts(transaction)
    this.total += transaction.amount
    if (transaction.amount < 0) {
      this.deposits -= transaction.amount
    }
  }

  private fun updateDateRange(transaction: Transaction) {
    if (this.start == null || start!!.isAfter(transaction.date)) {
      this.start = transaction.date
    }
    if (this.end == null || end!!.isBefore(transaction.date)) {
      this.end = transaction.date
    }
  }

  private fun updateAmounts(transaction: Transaction) {
    this.minAmount = min(this.minAmount, transaction.amount)
    this.maxAmount = max(this.maxAmount, transaction.amount)
  }

  private fun validateDateRange() {
    require(!(this.start == null || (this.end == null) || (this.start == this.end))) { "Invalid date range for transactions." }
  }

  private fun validateTransactionAmounts() {
    require(!(this.minAmount >= 0 || this.maxAmount <= 0)) { "Need both positive and negative transactions." }
  }
}
