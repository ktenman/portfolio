package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

@Entity
@Table(name = "etf_positions")
@IdClass(EtfPositionId::class)
class EtfPosition(
  @Id
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "etf_instrument_id", nullable = false)
  var etfInstrument: Instrument,
  @Id
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "holding_id", nullable = false)
  var holding: EtfHolding,
  @Id
  @Column(name = "snapshot_date", nullable = false)
  var snapshotDate: LocalDate,
  @Column(name = "weight_percentage", nullable = false, precision = 8, scale = 4)
  var weightPercentage: BigDecimal,
  @Column(name = "position_rank")
  var positionRank: Int? = null,
  @Column(name = "market_cap", length = 50)
  var marketCap: String? = null,
  @Column(length = 50)
  var price: String? = null,
  @Column(name = "day_change", length = 50)
  var dayChange: String? = null,
) {
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  var createdAt: ZonedDateTime = ZonedDateTime.now()
}
