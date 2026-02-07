package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "price_snapshot")
class PriceSnapshot(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "instrument_id", nullable = false)
  var instrument: Instrument,
  @Column(name = "provider_name", nullable = false)
  @Enumerated(EnumType.STRING)
  var providerName: ProviderName,
  @Column(name = "snapshot_hour", nullable = false)
  var snapshotHour: Instant,
  @Column(name = "price", nullable = false)
  var price: BigDecimal,
) : BaseEntity()
