package ee.tenman.portfolio.exception

class CaptchaException(
  message: String,
) : RuntimeException(message)

class EntityNotFoundException(
  message: String,
) : RuntimeException(message)

class XirrCalculationException(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause)
