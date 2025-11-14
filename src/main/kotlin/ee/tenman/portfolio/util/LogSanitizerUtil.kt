package ee.tenman.portfolio.util

object LogSanitizerUtil {
  private val CONTROL_CHARS_REGEX = "[\n\r\t]".toRegex()

  fun sanitize(input: String?): String {
    if (input == null) return "null"
    return input.replace(CONTROL_CHARS_REGEX, "_")
  }

  fun sanitize(input: Collection<*>?): String {
    if (input == null) return "null"
    return input.joinToString(", ") { sanitize(it?.toString()) }
  }
}
