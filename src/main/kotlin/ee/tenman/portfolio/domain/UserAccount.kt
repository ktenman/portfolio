package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "user_account")
class UserAccount(
  @Column(unique = true, nullable = false)
  var email: String,

  @Column(name = "session_id", unique = true, nullable = true)
  var sessionId: String? = null
) : BaseEntity()
