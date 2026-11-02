package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "radio_programme")
class RadioProgramme(
  @Column(name = "external_id", nullable = false, unique = true)
  var externalId: Long,
  @Column(nullable = false)
  var name: String,
  @Column(nullable = false)
  var slug: String,
  @Column
  var description: String? = null,
  @Column(name = "record_every", nullable = false)
  var recordEvery: Boolean = false,
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var station: Station = Station.KUKU,
) : BaseEntity()
