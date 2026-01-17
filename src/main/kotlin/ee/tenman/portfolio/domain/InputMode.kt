package ee.tenman.portfolio.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class InputMode {
  PERCENTAGE,
  AMOUNT,
  ;

  @JsonValue
  fun toJson(): String = name.lowercase()

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromString(value: String): InputMode =
      entries.find { it.name.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown input mode: $value")
  }
}
