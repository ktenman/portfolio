package ee.tenman.portfolio.openrouter

import ee.tenman.portfolio.domain.AiModel
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Component
class OpenRouterCircuitBreaker(
  private val properties: OpenRouterProperties,
  private val clock: Clock = Clock.systemUTC(),
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val modelRequestTimes = ConcurrentHashMap<AiModel, AtomicLong>()
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
    AiModel.entries.forEach { model ->
      modelRequestTimes[model] = AtomicLong(0)
    }
  }

  fun selectModel(): ModelSelection {
    val state = circuitBreaker.state
    val model =
      when (state) {
        CircuitBreaker.State.CLOSED, CircuitBreaker.State.HALF_OPEN -> primaryModel
        else -> fallbackModel
      }
    return ModelSelection(model = model, fallbackTier = model.fallbackTier)
  }

  fun selectModelByTier(tier: Int): ModelSelection {
    val model = AiModel.entries.find { it.fallbackTier == tier } ?: fallbackModel
    return ModelSelection(model = model, fallbackTier = model.fallbackTier)
  }

  fun selectFallbackModel(): ModelSelection = ModelSelection(model = fallbackModel, fallbackTier = fallbackModel.fallbackTier)

  fun getCurrentModel(): String = selectModel().modelId

  fun isUsingFallback(): Boolean = circuitBreaker.state == CircuitBreaker.State.OPEN

  fun canMakeRequest(): Boolean =
    when {
      isUsingFallback() -> canUseModel(fallbackModel)
      else -> canUseModel(primaryModel)
    }

  fun tryAcquireRateLimit(isUsingFallback: Boolean): Boolean =
    when {
      isUsingFallback -> tryAcquireForModel(fallbackModel)
      else -> tryAcquireForModel(primaryModel)
    }

  fun tryAcquireForModel(model: AiModel): Boolean {
    val lastRequestTime = modelRequestTimes.getOrPut(model) { AtomicLong(0) }
    val rateLimitMs = calculateRateLimitMs(model)
    return tryAcquireWithRateLimit(lastRequestTime, rateLimitMs, model.name)
  }

  fun tryAcquirePrimary(): Boolean = tryAcquireForModel(primaryModel)

  fun tryAcquireFallback(): Boolean = tryAcquireForModel(fallbackModel)

  fun getWaitTimeMs(isUsingFallback: Boolean): Long = getWaitTimeMsForModel(if (isUsingFallback) fallbackModel else primaryModel)

  fun getWaitTimeMsForModel(model: AiModel): Long {
    val lastRequestTime = modelRequestTimes.getOrPut(model) { AtomicLong(0) }
    val rateLimitMs = calculateRateLimitMs(model)
    val elapsed = clock.millis() - lastRequestTime.get()
    return maxOf(0, rateLimitMs - elapsed)
  }

  private fun calculateRateLimitMs(model: AiModel): Long = MILLISECONDS_PER_MINUTE / model.rateLimitPerMinute

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

  private fun canUseModel(model: AiModel): Boolean {
    val lastRequestTime = modelRequestTimes.getOrPut(model) { AtomicLong(0) }
    return (clock.millis() - lastRequestTime.get()) >= calculateRateLimitMs(model)
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
    modelRequestTimes.values.forEach { it.set(0) }
  }
}
