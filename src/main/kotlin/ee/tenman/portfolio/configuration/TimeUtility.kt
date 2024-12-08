package ee.tenman.portfolio.configuration

object TimeUtility {
  enum class TimeUnit {
    SECONDS, MILLIS
  }

  fun durationInSeconds(startTime: Long): CustomDuration {
    return CustomDuration(startTime, TimeUnit.SECONDS)
  }

  fun duration(startTime: Long, unit: TimeUnit = TimeUnit.SECONDS): CustomDuration {
    return CustomDuration(startTime, unit)
  }

  private fun formatDuration(duration: Double): String {
    return String.format("%.3f", duration)
  }

  class CustomDuration(startTime: Long, unit: TimeUnit) {
    private val duration = when (unit) {
      TimeUnit.SECONDS -> (System.nanoTime() - startTime) / 1000000000.0
      TimeUnit.MILLIS -> (System.nanoTime() - startTime) / 1000000.0
    }

    override fun toString(): String {
      return formatDuration(duration)
    }

    fun toDouble(): Double = duration
  }
}
