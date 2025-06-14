package ee.tenman.portfolio.configuration

import java.util.concurrent.TimeUnit

object TimeUtility {
  fun durationInSeconds(startTime: Long): CustomDuration = CustomDuration(startTime, TimeUnit.SECONDS)

  fun duration(
    startTime: Long,
    unit: TimeUnit = TimeUnit.SECONDS,
  ): CustomDuration = CustomDuration(startTime, unit)

  private fun formatDuration(duration: Double): String = String.format("%.3f", duration)

  class CustomDuration(
    startTime: Long,
    unit: TimeUnit,
  ) {
    private val duration =
      when (unit) {
        TimeUnit.NANOSECONDS -> (System.nanoTime() - startTime).toDouble()
        TimeUnit.MICROSECONDS -> (System.nanoTime() - startTime) / 1000.0
        TimeUnit.MILLISECONDS -> (System.nanoTime() - startTime) / 1000000.0
        TimeUnit.SECONDS -> (System.nanoTime() - startTime) / 1000000000.0
        TimeUnit.MINUTES -> (System.nanoTime() - startTime) / 60000000000.0
        TimeUnit.HOURS -> (System.nanoTime() - startTime) / 3600000000000.0
        TimeUnit.DAYS -> (System.nanoTime() - startTime) / 86400000000000.0
      }

    override fun toString(): String = formatDuration(duration)

    fun toDouble(): Double = duration
  }
}
