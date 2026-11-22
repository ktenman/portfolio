package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "user_preference")
class UserPreference(
  @Column(nullable = false)
  var userId: String,
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var theme: Theme,
) : BaseEntity()
