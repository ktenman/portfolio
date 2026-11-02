package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "radio_airing")
class RadioAiring(
  @Column(name = "external_id", nullable = false, unique = true)
  var externalId: Long,
  @Column(name = "programme_id", nullable = false)
  var programmeId: Long,
  @Column(nullable = false)
  var title: String,
  @Column(name = "starts_at", nullable = false)
  var startsAt: Instant,
  @Column(name = "ends_at", nullable = false)
  var endsAt: Instant,
  @Column(name = "original_id")
  var originalId: Long? = null,
  @Column(name = "record_once", nullable = false)
  var recordOnce: Boolean = false,
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var station: Station = Station.KUKU,
) : BaseEntity()
