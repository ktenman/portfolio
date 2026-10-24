package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "radio_position")
class RadioPosition(
  @Column(nullable = false)
  var listener: String,
  @Column(nullable = false)
  var recording: String,
  @Column(nullable = false)
  var seconds: Double,
) : BaseEntity()
