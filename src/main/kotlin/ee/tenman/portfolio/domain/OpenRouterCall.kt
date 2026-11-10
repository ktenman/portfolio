package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "openrouter_call")
class OpenRouterCall(
  @Column(nullable = false)
  val day: LocalDate,
  @Column(nullable = false)
  val purpose: String,
  @Column(nullable = false)
  val model: String,
  @Column(nullable = false)
  val cost: BigDecimal,
) : BaseEntity()
