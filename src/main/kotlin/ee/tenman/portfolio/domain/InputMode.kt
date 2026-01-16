package ee.tenman.portfolio.domain

enum class InputMode {
  PERCENTAGE,
  AMOUNT,
  ;

  companion object {
    fun fromString(value: String): InputMode =
      entries.find { it.name.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown input mode: $value")
  }
}
