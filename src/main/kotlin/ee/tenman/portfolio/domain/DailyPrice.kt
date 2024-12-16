package ee.tenman.portfolio.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "daily_price")
class DailyPrice(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "instrument_id", nullable = false)
  var instrument: Instrument,

  @Column(name = "entry_date", nullable = false)
  var entryDate: LocalDate,

  @Column(name = "provider_name", nullable = false)
  @Enumerated(EnumType.STRING)
  var providerName: ProviderName,

  @Column(name = "open_price")
  var openPrice: BigDecimal?,

  @Column(name = "high_price")
  var highPrice: BigDecimal?,

  @Column(name = "low_price")
  var lowPrice: BigDecimal?,

  @Column(name = "close_price", nullable = false)
  var closePrice: BigDecimal,

  @Column
  var volume: Long?
) : BaseEntity()
