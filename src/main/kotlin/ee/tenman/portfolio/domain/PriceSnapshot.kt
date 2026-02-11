package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
  name = "price_snapshot",
  uniqueConstraints = [UniqueConstraint(columnNames = ["instrument_id", "provider_name", "snapshot_hour"])],
)
class PriceSnapshot(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "instrument_id", nullable = false)
  val instrument: Instrument,
  @Column(name = "provider_name", nullable = false)
  @Enumerated(EnumType.STRING)
  val providerName: ProviderName,
  @Column(name = "snapshot_hour", nullable = false)
  val snapshotHour: Instant,
  @Column(name = "price", nullable = false)
  val price: BigDecimal,
) : BaseEntity()
