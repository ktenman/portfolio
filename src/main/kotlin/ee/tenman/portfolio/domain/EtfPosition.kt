package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
  name = "etf_position",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["etf_instrument_id", "holding_id", "snapshot_date"]),
  ],
)
class EtfPosition(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "etf_instrument_id", nullable = false)
  var etfInstrument: Instrument,
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "holding_id", nullable = false)
  var holding: EtfHolding,
  @Column(name = "snapshot_date", nullable = false)
  var snapshotDate: LocalDate,
  @Column(name = "weight_percentage", nullable = false, precision = 22, scale = 12)
  var weightPercentage: BigDecimal,
  @Column(name = "position_rank")
  var positionRank: Int? = null,
) : BaseEntity()
