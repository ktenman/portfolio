package ee.tenman.portfolio.auth

import ee.tenman.portfolio.domain.UserAccount
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserService {
  private val log = LoggerFactory.getLogger(UserService::class.java)

  /**
   * @return the current user's UserInfo.
   */
  fun getCurrentUserInfo(): Optional<UserAccount> {
    val userAccount = UserContextHolder.getUserAccount()
    log.debug("Found userInfo '{}' in custom context", userAccount)
    return Optional.ofNullable(userAccount)
  }
}
