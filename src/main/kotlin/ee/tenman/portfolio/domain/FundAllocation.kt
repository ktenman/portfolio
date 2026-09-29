package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "fund_allocation")
class FundAllocation(
  @Column(nullable = false)
  val fundIsin: String,
  @Column(nullable = false)
  val asOfDate: LocalDate,
  @Column(nullable = false, columnDefinition = "TEXT")
  val sourceUrl: String,
  @Column(nullable = false)
  val reportedTotal: BigDecimal,
  @Column(nullable = false)
  val underlyingIsin: String,
  @Column(nullable = false)
  val underlyingName: String,
  @Column(nullable = false)
  val weight: BigDecimal,
  val repairedFrom: String? = null,
) : BaseEntity()
