package ee.tenman.portfolio.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class ActionDisplayMode {
  UNITS,
  AMOUNT,
  ;

  @JsonValue
  fun toJson(): String = name.lowercase()

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromString(value: String): ActionDisplayMode =
      entries.find { it.name.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown action display mode: $value")
  }
}
