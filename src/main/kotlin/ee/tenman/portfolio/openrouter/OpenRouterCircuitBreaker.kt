package ee.tenman.portfolio.openrouter

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Component
class OpenRouterCircuitBreaker(
  private val properties: OpenRouterProperties,
  private val clock: Clock = Clock.systemUTC(),
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val lastPrimaryRequestTime = AtomicLong(0)
  private val lastFallbackRequestTime = AtomicLong(0)
  private val circuitBreaker: CircuitBreaker
  private val primaryModel = properties.primaryModel
  private val fallbackModel = properties.fallbackModel

  companion object {
    private const val MILLISECONDS_PER_MINUTE = 60_000L
  }

  init {
    val config =
      CircuitBreakerConfig
        .custom()
        .failureRateThreshold(100f)
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(properties.circuitBreaker.failureThreshold)
        .minimumNumberOfCalls(properties.circuitBreaker.failureThreshold)
        .waitDurationInOpenState(Duration.ofSeconds(properties.circuitBreaker.recoveryTimeoutSeconds))
        .permittedNumberOfCallsInHalfOpenState(1)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build()
    val registry = CircuitBreakerRegistry.of(config)
    circuitBreaker = registry.circuitBreaker("openrouter")
    circuitBreaker.eventPublisher
      .onStateTransition { event ->
        log.info(
          "Circuit breaker state transition: {} -> {}",
          event.stateTransition.fromState,
          event.stateTransition.toState,
        )
      }
  }

  fun selectModel(): ModelSelection {
    val state = circuitBreaker.state
    val model =
      when (state) {
        CircuitBreaker.State.CLOSED, CircuitBreaker.State.HALF_OPEN -> primaryModel
        else -> fallbackModel
      }
    return ModelSelection(
      modelId = model.modelId,
      isUsingFallback = state == CircuitBreaker.State.OPEN,
    )
  }

  fun selectFallbackModel(): ModelSelection =
    ModelSelection(
      modelId = fallbackModel.modelId,
      isUsingFallback = true,
    )

  fun getCurrentModel(): String = selectModel().modelId

  fun isUsingFallback(): Boolean = circuitBreaker.state == CircuitBreaker.State.OPEN

  fun canMakeRequest(): Boolean =
    when {
      isUsingFallback() -> canUseFallback()
      else -> canUsePrimary()
    }

  fun tryAcquireRateLimit(isUsingFallback: Boolean): Boolean =
    when {
      isUsingFallback -> tryAcquireFallback()
      else -> tryAcquirePrimary()
    }

  fun tryAcquirePrimary(): Boolean = tryAcquireWithRateLimit(lastPrimaryRequestTime, primaryRateLimitMs(), "Primary")

  fun tryAcquireFallback(): Boolean = tryAcquireWithRateLimit(lastFallbackRequestTime, fallbackRateLimitMs(), "Fallback")

  fun getWaitTimeMs(isUsingFallback: Boolean): Long {
    val lastRequestTime = if (isUsingFallback) lastFallbackRequestTime else lastPrimaryRequestTime
    val rateLimitMs = if (isUsingFallback) fallbackRateLimitMs() else primaryRateLimitMs()
    val elapsed = clock.millis() - lastRequestTime.get()
    return maxOf(0, rateLimitMs - elapsed)
  }

  private fun primaryRateLimitMs(): Long = MILLISECONDS_PER_MINUTE / primaryModel.rateLimitPerMinute

  private fun fallbackRateLimitMs(): Long = MILLISECONDS_PER_MINUTE / fallbackModel.rateLimitPerMinute

  private fun tryAcquireWithRateLimit(
    lastRequestTime: AtomicLong,
    rateLimitMs: Long,
    modelType: String,
  ): Boolean {
    while (true) {
      val now = clock.millis()
      val lastRequest = lastRequestTime.get()
      if ((now - lastRequest) < rateLimitMs) {
        log.debug("{} rate limit active, {} ms until next request allowed", modelType, rateLimitMs - (now - lastRequest))
        return false
      }
      if (lastRequestTime.compareAndSet(lastRequest, now)) {
        return true
      }
    }
  }

  fun recordSuccess() {
    circuitBreaker.onSuccess(0, TimeUnit.MILLISECONDS)
    log.debug("Recorded success, circuit breaker state: {}", circuitBreaker.state)
  }

  fun recordFailure(throwable: Throwable) {
    circuitBreaker.onError(0, TimeUnit.MILLISECONDS, throwable)
    log.warn("Recorded failure, circuit breaker state: {}", circuitBreaker.state)
  }

  private fun canUsePrimary(): Boolean = (clock.millis() - lastPrimaryRequestTime.get()) >= primaryRateLimitMs()

  private fun canUseFallback(): Boolean = (clock.millis() - lastFallbackRequestTime.get()) >= fallbackRateLimitMs()

  fun getState(): CircuitBreaker.State = circuitBreaker.state

  fun transitionToHalfOpenState() {
    circuitBreaker.transitionToHalfOpenState()
  }

  fun reset() {
    circuitBreaker.reset()
    resetRateLimits()
  }

  fun resetRateLimits() {
    lastPrimaryRequestTime.set(0)
    lastFallbackRequestTime.set(0)
  }
}
