package ee.tenman.portfolio.configuration

object TimeUtility {

  fun durationInSeconds(startTime: Long): CustomDuration {
    return CustomDuration(startTime)
  }

  private fun formatDuration(duration: Double): String {
    return String.format("%.3f", duration)
  }

  class CustomDuration(startTime: Long) {
    private val durationInSeconds = (System.nanoTime() - startTime) / 1000000000.0

    override fun toString(): String {
      return formatDuration(durationInSeconds)
    }
  }
}
