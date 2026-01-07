package ee.tenman.portfolio.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

object XirrPolicy {
  val minimumHoldingPeriod: Duration = Duration.ofDays(30)
  val fullWeightPeriod: Duration = Duration.ofDays(61)
  const val MINIMUM_DAYS_FOR_CALCULATION: Double = 365.25 / 12
  const val FULL_DAMPING_DAYS: Double = 365.25 / 6
  const val XIRR_MIN_BOUND: Double = -10.0
  const val XIRR_MAX_BOUND: Double = 10.0
  private const val CALCULATION_SCALE = 10

  fun dampingFactor(holdingDays: Long): BigDecimal =
    when {
    holdingDays < minimumHoldingPeriod.toDays() -> BigDecimal.ZERO
    holdingDays >= fullWeightPeriod.toDays() -> BigDecimal.ONE
    else ->
      BigDecimal(holdingDays)
      .divide(BigDecimal(fullWeightPeriod.toDays()), CALCULATION_SCALE, RoundingMode.HALF_UP)
  }

  fun isEligibleForCalculation(weightedDays: Double): Boolean = weightedDays >= MINIMUM_DAYS_FOR_CALCULATION

  fun applyDamping(
    xirrResult: Double,
    weightedDays: Double,
  ): Double {
    val factor = minOf(1.0, weightedDays / FULL_DAMPING_DAYS)
    return xirrResult.coerceIn(XIRR_MIN_BOUND, XIRR_MAX_BOUND) * factor
  }
}
