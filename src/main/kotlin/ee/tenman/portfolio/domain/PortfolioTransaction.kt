package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "portfolio_transaction")
class PortfolioTransaction(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "instrument_id", nullable = false)
  var instrument: Instrument,
  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false)
  var transactionType: TransactionType,
  @Column(nullable = false)
  var quantity: BigDecimal,
  @Column(nullable = false)
  var price: BigDecimal,
  @Column(name = "transaction_date", nullable = false)
  var transactionDate: LocalDate,
  @Enumerated(EnumType.STRING)
  @Column(name = "platform", nullable = false)
  var platform: Platform,
  @Column(name = "realized_profit")
  var realizedProfit: BigDecimal? = null,
  @Column(name = "unrealized_profit")
  var unrealizedProfit: BigDecimal = BigDecimal.ZERO, // Initialize with ZERO
  @Column(name = "average_cost")
  var averageCost: BigDecimal? = null,
  @Column(name = "remaining_quantity")
  var remainingQuantity: BigDecimal = BigDecimal.ZERO,
  @Column(name = "commission", nullable = false)
  var commission: BigDecimal = BigDecimal.ZERO,
  @Column(name = "currency", nullable = false)
  var currency: String = "EUR",
) : BaseEntity() {
  private fun writeObject(output: ObjectOutputStream) {
    output.writeObject(instrument)
    output.writeObject(transactionType)
    output.writeObject(platform)
    output.writeLong(transactionDate.toEpochDay())
    output.writeUTF(currency)
    listOf(
      quantity,
      price,
      realizedProfit,
      unrealizedProfit,
      averageCost,
      remainingQuantity,
      commission,
    ).forEach { output.writeDecimal(it) }
  }

  private fun readObject(input: ObjectInputStream) {
    instrument = input.readObject() as Instrument
    transactionType = input.readObject() as TransactionType
    platform = input.readObject() as Platform
    transactionDate = LocalDate.ofEpochDay(input.readLong())
    currency = input.readUTF()
    quantity = input.readDecimal()
    price = input.readDecimal()
    realizedProfit = input.readOptionalDecimal()
    input.readOptionalDecimal()?.let { unrealizedProfit = it }
    averageCost = input.readOptionalDecimal()
    input.readOptionalDecimal()?.let { remainingQuantity = it }
    commission = input.readDecimal()
  }

  companion object {
    private const val serialVersionUID: Long = -5966201828531052108L
  }
}
