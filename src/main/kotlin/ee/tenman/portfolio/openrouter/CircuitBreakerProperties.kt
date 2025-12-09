package ee.tenman.portfolio.openrouter

data class CircuitBreakerProperties(
  val failureThreshold: Int = 3,
  val recoveryTimeoutSeconds: Long = 60,
) {
  init {
    require(failureThreshold > 0) { "failureThreshold must be positive" }
    require(recoveryTimeoutSeconds > 0) { "recoveryTimeoutSeconds must be positive" }
  }
}
