package ee.tenman.portfolio.auto24

data class CaptchaResponse(
  val sessionId: String? = null,
  val captchaImage: String? = null,
  val status: String? = null,
  val price: String? = null,
  val message: String? = null,
)
