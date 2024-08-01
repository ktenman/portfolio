package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.USER_SESSION_ID_CACHE
import ee.tenman.portfolio.domain.UserAccount
import ee.tenman.portfolio.repository.UserAccountRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAccountService(private val userAccountRepository: UserAccountRepository) {

  @Transactional
  fun getOrCreateByEmail(email: String): UserAccount =
    userAccountRepository.findByEmail(email) ?: createNewUserAccount(email)

  @Transactional(readOnly = true)
  @Cacheable(value = [USER_SESSION_ID_CACHE], key = "#sessionId")
  fun findBySessionId(sessionId: String): UserAccount? =
    userAccountRepository.findBySessionId(sessionId)

  @Transactional
  fun save(userAccount: UserAccount): UserAccount = userAccountRepository.save(userAccount)

  private fun createNewUserAccount(email: String): UserAccount =
    UserAccount(email = email).let(userAccountRepository::save)
}
