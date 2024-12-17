package ee.tenman.portfolio.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.jvm.Transient

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
  @Column(name = "platform", nullable = true)
  var platform: Platform,

  @Transient
  var currentValue: BigDecimal = BigDecimal.ZERO,

  @Transient
  var profit: BigDecimal = BigDecimal.ZERO,

  @Column(name = "realized_profit", nullable = true)
  var realizedProfit: BigDecimal? = null,

  @Column(name = "average_cost", nullable = true)
  var averageCost: BigDecimal? = null,

  @Transient
  var unrealizedProfit: BigDecimal = BigDecimal.ZERO
) : BaseEntity()
