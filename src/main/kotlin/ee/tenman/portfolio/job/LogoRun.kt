package ee.tenman.portfolio.job

private const val MAX_FAILURES_IN_A_ROW = 3

sealed interface LogoOutcome {
  data object Answered : LogoOutcome

  data object Skipped : LogoOutcome

  data class Failed(
    val reason: String,
  ) : LogoOutcome
}

data class LogoRun(
  val tried: Int = 0,
  val failed: Int = 0,
  val streak: Int = 0,
  val error: String? = null,
) {
  val stopped: Boolean get() = streak >= MAX_FAILURES_IN_A_ROW
  val broken: Boolean get() = stopped || (tried > 0 && failed == tried)
  val message: String get() = "Tried $tried holdings, $failed failed" + error?.let { ", last error: $it" }.orEmpty()

  fun after(outcome: LogoOutcome): LogoRun =
    when (outcome) {
      LogoOutcome.Skipped -> this
      LogoOutcome.Answered -> copy(tried = tried + 1, streak = 0)
      is LogoOutcome.Failed -> copy(tried = tried + 1, failed = failed + 1, streak = streak + 1, error = outcome.reason)
    }
}
