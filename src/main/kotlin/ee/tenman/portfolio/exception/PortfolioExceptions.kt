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

class PriceRefreshException(
  message: String,
) : IllegalStateException(message)

class LogoSelectionFailedException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

class LogoSearchUnavailableException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

class RecordingInProgressException(
  name: String,
) : IllegalStateException("Recording $name is still being recorded")
