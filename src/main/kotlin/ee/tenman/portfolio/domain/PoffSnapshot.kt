package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "poff_snapshot")
class PoffSnapshot(
  @Column(nullable = false, columnDefinition = "text")
  var catalogue: PoffCatalogue?,
  @Column(nullable = false)
  var syncedAt: Instant,
) : BaseEntity()
