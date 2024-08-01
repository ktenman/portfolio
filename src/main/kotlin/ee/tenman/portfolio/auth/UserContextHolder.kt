package ee.tenman.portfolio.auth

import ee.tenman.portfolio.auth.model.UserInfo

object UserContextHolder {
  private val userContext = ThreadLocal<UserInfo>()

  fun setUserInfo(userInfo: UserInfo?) {
    userContext.set(userInfo)
  }

  fun getUserInfo(): UserInfo? {
    return userContext.get()
  }

  fun getEmail(): String? {
    return userContext.get()?.email
  }

  fun clear() {
    userContext.remove()
  }
}
