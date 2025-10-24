package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
  name = "etf_holding",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["name", "ticker"]),
  ],
)
class EtfHolding(
  @Column(length = 50)
  var ticker: String? = null,
  @Column(nullable = false, length = 255)
  var name: String,
  @Column(length = 150)
  var sector: String? = null,
) : BaseEntity()
