package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "radio_player")
class RadioPlayer(
  @Column(nullable = false)
  var listener: String,
  var recording: String? = null,
  @Enumerated(EnumType.STRING)
  var station: Station? = null,
  var x: Double? = null,
  var y: Double? = null,
) : BaseEntity()
