package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "pending_holding_import")
class PendingHoldingImport(
  @Column(nullable = false)
  val etfSymbol: String,
  @Column(nullable = false)
  val snapshotDate: LocalDate,
  @Column(nullable = false, columnDefinition = "TEXT")
  val holdings: String,
) : BaseEntity()
