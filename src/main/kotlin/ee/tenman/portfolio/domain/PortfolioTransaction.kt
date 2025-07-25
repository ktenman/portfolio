package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
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
) : BaseEntity()
