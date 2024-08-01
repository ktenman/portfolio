package ee.tenman.portfolio.repository

import ee.tenman.portfolio.domain.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserAccountRepository : JpaRepository<UserAccount, Long> {
  fun findByEmail(email: String): UserAccount?
  fun findBySessionId(sessionId: String): UserAccount?
}
