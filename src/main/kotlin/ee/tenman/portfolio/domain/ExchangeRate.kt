package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "exchange_rate")
class ExchangeRate(
  @Column(name = "entry_date", nullable = false)
  var entryDate: LocalDate,
  @Column(name = "base_currency", nullable = false)
  @Enumerated(EnumType.STRING)
  var baseCurrency: Currency,
  @Column(name = "quote_currency", nullable = false)
  @Enumerated(EnumType.STRING)
  var quoteCurrency: Currency,
  @Column(nullable = false)
  var rate: BigDecimal,
) : BaseEntity()
