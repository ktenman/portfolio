package ee.tenman.portfolio.domain

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.io.Serializable
import java.time.Instant

@MappedSuperclass
abstract class BaseEntity : Serializable {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long = 0

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  lateinit var createdAt: Instant

  @UpdateTimestamp
  @Column(nullable = false)
  lateinit var updatedAt: Instant

  @Version
  @Column(nullable = false)
  var version: Long = 0
}
