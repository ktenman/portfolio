package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "instrument")
class Instrument(
  @Column(unique = true, nullable = false)
  var symbol: String,

  @Column(nullable = true)
  var name: String,

  @Column(name = "instrument_category", nullable = true)
  var category: String,

  @Column(name = "base_currency", nullable = false)
  var baseCurrency: String
) : BaseEntity()
