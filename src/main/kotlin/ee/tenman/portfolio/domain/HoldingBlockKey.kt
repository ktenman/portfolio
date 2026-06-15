package ee.tenman.portfolio.domain

object HoldingBlockKey {
  private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

  fun of(name: String): String =
    name
      .map { it.lowercaseChar() }
      .joinToString("")
      .split(NON_ALPHANUMERIC)
      .firstOrNull { it.isNotEmpty() }
      ?: ""
}
