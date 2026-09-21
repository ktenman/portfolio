package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
  name = "instrument_minute_price",
  uniqueConstraints = [UniqueConstraint(columnNames = ["instrument_id", "captured_at"])],
)
class InstrumentMinutePrice(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "instrument_id", nullable = false)
  val instrument: Instrument,
  @Column(name = "captured_at", nullable = false)
  val capturedAt: Instant,
  @Column(name = "price", nullable = false)
  val price: BigDecimal,
) : BaseEntity()
