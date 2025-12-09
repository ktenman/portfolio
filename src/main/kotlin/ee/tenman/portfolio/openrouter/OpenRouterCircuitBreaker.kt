package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiModel
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
  private val primaryModel = AiModel.CLAUDE_3_HAIKU
  private val fallbackModel = AiModel.CLAUDE_HAIKU_4_5

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

  fun tryAcquirePrimary(): Boolean {
    val rateLimitMs = MILLISECONDS_PER_MINUTE / primaryModel.rateLimitPerMinute
    return tryAcquireWithRateLimit(lastPrimaryRequestTime, rateLimitMs, "Primary")
  }

  fun tryAcquireFallback(): Boolean {
    val rateLimitMs = MILLISECONDS_PER_MINUTE / fallbackModel.rateLimitPerMinute
    return tryAcquireWithRateLimit(lastFallbackRequestTime, rateLimitMs, "Fallback")
  }

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

  fun recordFailure(exception: Exception) {
    circuitBreaker.onError(0, TimeUnit.MILLISECONDS, exception)
    log.warn("Recorded failure, circuit breaker state: {}", circuitBreaker.state)
  }

  private fun canUsePrimary(): Boolean {
    val now = clock.millis()
    val lastRequest = lastPrimaryRequestTime.get()
    val rateLimitMs = MILLISECONDS_PER_MINUTE / primaryModel.rateLimitPerMinute
    return (now - lastRequest) >= rateLimitMs
  }

  private fun canUseFallback(): Boolean {
    val now = clock.millis()
    val lastRequest = lastFallbackRequestTime.get()
    val rateLimitMs = MILLISECONDS_PER_MINUTE / fallbackModel.rateLimitPerMinute
    return (now - lastRequest) >= rateLimitMs
  }

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
