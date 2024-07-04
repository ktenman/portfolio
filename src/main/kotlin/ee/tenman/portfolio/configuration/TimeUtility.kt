package ee.tenman.portfolio.configuration

import lombok.AccessLevel
import lombok.NoArgsConstructor

@NoArgsConstructor(access = AccessLevel.PRIVATE)
object TimeUtility {
  fun durationInSeconds(startTime: Long): CustomDuration {
    return CustomDuration(startTime)
  }

  private fun formatDuration(duration: Double): String {
    return String.format("%.3f", duration)
  }

  class CustomDuration(startTime: Long) {
    private val durationInSeconds = (System.nanoTime() - startTime) / 1000000000.0

    fun asString(): String {
      return formatDuration(durationInSeconds)
    }

    override fun toString(): String {
      return formatDuration(durationInSeconds)
    }
  }
}
