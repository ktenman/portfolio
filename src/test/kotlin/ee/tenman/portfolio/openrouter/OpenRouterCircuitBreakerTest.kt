package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.AiModel
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class OpenRouterCircuitBreakerTest {
  private lateinit var circuitBreaker: OpenRouterCircuitBreaker
  private lateinit var properties: OpenRouterProperties
  private lateinit var clock: MutableClock

  @BeforeEach
  fun setUp() {
    properties =
      OpenRouterProperties(
        apiKey = "test-key",
        circuitBreaker =
          CircuitBreakerProperties(
            failureThreshold = 3,
            recoveryTimeoutSeconds = 60,
          ),
      )
    clock = MutableClock(Instant.now())
    circuitBreaker = OpenRouterCircuitBreaker(properties, clock)
  }

  @Test
  fun `should return primary model when circuit is closed`() {
    expect(circuitBreaker.getCurrentModel()).toEqual(AiModel.CLAUDE_3_HAIKU.modelId)
  }

  @Test
  fun `should return primary model state as closed initially`() {
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.CLOSED)
  }

  @Test
  fun `should not be using fallback when circuit is closed`() {
    expect(circuitBreaker.isUsingFallback()).toEqual(false)
  }

  @Test
  fun `should allow request when circuit is closed`() {
    expect(circuitBreaker.canMakeRequest()).toEqual(true)
  }

  @Test
  fun `should switch to fallback model after failure threshold reached`() {
    repeat(3) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.OPEN)
    expect(circuitBreaker.getCurrentModel()).toEqual(AiModel.CLAUDE_HAIKU_4_5.modelId)
    expect(circuitBreaker.isUsingFallback()).toEqual(true)
  }

  @Test
  fun `should stay closed when failures are below threshold`() {
    repeat(2) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.CLOSED)
    expect(circuitBreaker.getCurrentModel()).toEqual(AiModel.CLAUDE_3_HAIKU.modelId)
  }

  @Test
  fun `should reset failure count on success`() {
    circuitBreaker.recordFailure(Exception("API error"))
    circuitBreaker.recordFailure(Exception("API error"))
    circuitBreaker.recordSuccess()
    circuitBreaker.recordFailure(Exception("API error"))
    circuitBreaker.recordFailure(Exception("API error"))
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.CLOSED)
  }

  @Test
  fun `should allow first fallback request`() {
    repeat(3) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    expect(circuitBreaker.canMakeRequest()).toEqual(true)
  }

  @Test
  fun `should rate limit fallback requests with tryAcquireFallback`() {
    repeat(3) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    expect(circuitBreaker.tryAcquireFallback()).toEqual(true)
    expect(circuitBreaker.tryAcquireFallback()).toEqual(false)
  }

  @Test
  fun `should allow fallback request after rate limit period`() {
    repeat(3) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    expect(circuitBreaker.tryAcquireFallback()).toEqual(true)
    expect(circuitBreaker.canMakeRequest()).toEqual(false)
    clock.advance(8572)
    expect(circuitBreaker.canMakeRequest()).toEqual(true)
  }

  @Test
  fun `should rate limit primary requests`() {
    expect(circuitBreaker.tryAcquirePrimary()).toEqual(true)
    expect(circuitBreaker.tryAcquirePrimary()).toEqual(false)
  }

  @Test
  fun `should allow primary request after rate limit period`() {
    expect(circuitBreaker.tryAcquirePrimary()).toEqual(true)
    expect(circuitBreaker.canMakeRequest()).toEqual(false)
    clock.advance(2001)
    expect(circuitBreaker.canMakeRequest()).toEqual(true)
  }

  @Test
  fun `should use tryAcquireRateLimit for primary model`() {
    expect(circuitBreaker.tryAcquireRateLimit(isUsingFallback = false)).toEqual(true)
    expect(circuitBreaker.tryAcquireRateLimit(isUsingFallback = false)).toEqual(false)
  }

  @Test
  fun `should use tryAcquireRateLimit for fallback model`() {
    repeat(3) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    expect(circuitBreaker.tryAcquireRateLimit(isUsingFallback = true)).toEqual(true)
    expect(circuitBreaker.tryAcquireRateLimit(isUsingFallback = true)).toEqual(false)
  }

  @Test
  fun `should select model atomically`() {
    val selection = circuitBreaker.selectModel()
    expect(selection.modelId).toEqual(AiModel.CLAUDE_3_HAIKU.modelId)
    expect(selection.isUsingFallback).toEqual(false)
  }

  @Test
  fun `should select fallback model when circuit is open`() {
    repeat(3) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    val selection = circuitBreaker.selectModel()
    expect(selection.modelId).toEqual(AiModel.CLAUDE_HAIKU_4_5.modelId)
    expect(selection.isUsingFallback).toEqual(true)
  }

  @Test
  fun `should return primary model in half open state`() {
    repeat(3) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.OPEN)
    circuitBreaker.transitionToHalfOpenState()
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.HALF_OPEN)
    expect(circuitBreaker.getCurrentModel()).toEqual(AiModel.CLAUDE_3_HAIKU.modelId)
  }

  @Test
  fun `should close circuit after success in half open state`() {
    repeat(3) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    circuitBreaker.transitionToHalfOpenState()
    circuitBreaker.recordSuccess()
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.CLOSED)
  }

  private class MutableClock(
    private var instant: Instant,
  ) : Clock() {
    override fun instant(): Instant = instant

    override fun withZone(zone: ZoneId?): Clock = this

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    fun advance(millis: Long) {
      instant = instant.plusMillis(millis)
    }
  }
}
