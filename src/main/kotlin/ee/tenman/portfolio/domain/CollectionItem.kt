package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(name = "collection_item", uniqueConstraints = [UniqueConstraint(columnNames = ["collection_key", "symbol"])])
class CollectionItem(
  @Enumerated(EnumType.STRING)
  @Column(name = "collection_key", nullable = false)
  val key: CollectionKey,
  @Column(name = "symbol", nullable = false)
  val symbol: String,
  @Column(name = "initialized_at", nullable = false)
  val initializedAt: Instant,
  @Column(name = "active", nullable = false)
  var active: Boolean = true,
  @Column(name = "last_success")
  var lastSuccess: Instant? = null,
  @Column(name = "last_error")
  var lastError: String? = null,
) : BaseEntity()
