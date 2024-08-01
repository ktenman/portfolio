package ee.tenman.portfolio.auth

import ee.tenman.portfolio.auth.model.UserInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Optional

@Service
class UserService {
  private val log = LoggerFactory.getLogger(UserService::class.java)

  /**
   * @return the current user's UserInfo.
   */
  fun getCurrentUserInfo(): Optional<UserInfo> {
    val userInfo = UserContextHolder.getUserInfo()
    log.debug("Found userInfo '{}' in custom context", userInfo)
    return Optional.ofNullable(userInfo)
  }
}
