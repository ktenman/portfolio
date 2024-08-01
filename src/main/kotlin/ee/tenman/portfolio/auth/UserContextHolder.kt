package ee.tenman.portfolio.auth

import ee.tenman.portfolio.domain.UserAccount

object UserContextHolder {
  private val userAccountThreadLocal = ThreadLocal<UserAccount>()

  fun setUserAccount(userAccount: UserAccount) {
    userAccountThreadLocal.set(userAccount)
  }

  fun getUserAccount(): UserAccount? = userAccountThreadLocal.get()

  fun clear() {
    userAccountThreadLocal.remove()
  }
}
