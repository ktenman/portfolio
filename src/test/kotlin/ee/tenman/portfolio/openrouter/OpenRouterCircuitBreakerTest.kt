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

  companion object {
    private const val MILLISECONDS_PER_MINUTE = 60_000L
    private const val RATE_LIMIT_BUFFER_MS = 1L
    private val PRIMARY_RATE_LIMIT_INTERVAL_MS =
      (MILLISECONDS_PER_MINUTE / AiModel.GEMINI_3_FLASH_PREVIEW.rateLimitPerMinute) + RATE_LIMIT_BUFFER_MS
    private val FALLBACK_RATE_LIMIT_INTERVAL_MS =
      (MILLISECONDS_PER_MINUTE / AiModel.CLAUDE_OPUS_4_5.rateLimitPerMinute) + RATE_LIMIT_BUFFER_MS
  }

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
    expect(circuitBreaker.getCurrentModel()).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW.modelId)
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
    expect(circuitBreaker.getCurrentModel()).toEqual(AiModel.CLAUDE_OPUS_4_5.modelId)
    expect(circuitBreaker.isUsingFallback()).toEqual(true)
  }

  @Test
  fun `should stay closed when failures are below threshold`() {
    repeat(2) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.CLOSED)
    expect(circuitBreaker.getCurrentModel()).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW.modelId)
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
    clock.advance(FALLBACK_RATE_LIMIT_INTERVAL_MS)
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
    clock.advance(PRIMARY_RATE_LIMIT_INTERVAL_MS)
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
    expect(selection.modelId).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW.modelId)
    expect(selection.isUsingFallback).toEqual(false)
  }

  @Test
  fun `should select fallback model when circuit is open`() {
    repeat(3) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    val selection = circuitBreaker.selectModel()
    expect(selection.modelId).toEqual(AiModel.CLAUDE_OPUS_4_5.modelId)
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
    expect(circuitBreaker.getCurrentModel()).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW.modelId)
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

  @Test
  fun `should return zero wait time when no previous request`() {
    expect(circuitBreaker.getWaitTimeMs(isUsingFallback = false)).toEqual(0L)
  }

  @Test
  fun `should return remaining wait time after primary request`() {
    circuitBreaker.tryAcquirePrimary()
    val waitTime = circuitBreaker.getWaitTimeMs(isUsingFallback = false)
    val expectedMs = MILLISECONDS_PER_MINUTE / AiModel.GEMINI_3_FLASH_PREVIEW.rateLimitPerMinute
    expect(waitTime).toEqual(expectedMs)
  }

  @Test
  fun `should return zero wait time after rate limit period elapsed`() {
    circuitBreaker.tryAcquirePrimary()
    clock.advance(PRIMARY_RATE_LIMIT_INTERVAL_MS)
    expect(circuitBreaker.getWaitTimeMs(isUsingFallback = false)).toEqual(0L)
  }

  @Test
  fun `should return remaining wait time for fallback model`() {
    repeat(3) {
      circuitBreaker.recordFailure(Exception("API error"))
    }
    circuitBreaker.tryAcquireFallback()
    val waitTime = circuitBreaker.getWaitTimeMs(isUsingFallback = true)
    val expectedMs = MILLISECONDS_PER_MINUTE / AiModel.CLAUDE_OPUS_4_5.rateLimitPerMinute
    expect(waitTime).toEqual(expectedMs)
  }

  @Test
  fun `should rate limit DEEPSEEK_V3_2 at 60 requests per minute`() {
    expect(circuitBreaker.tryAcquireForModel(AiModel.DEEPSEEK_V3_2)).toEqual(true)
    expect(circuitBreaker.tryAcquireForModel(AiModel.DEEPSEEK_V3_2)).toEqual(false)
    clock.advance((MILLISECONDS_PER_MINUTE / AiModel.DEEPSEEK_V3_2.rateLimitPerMinute) + RATE_LIMIT_BUFFER_MS)
    expect(circuitBreaker.tryAcquireForModel(AiModel.DEEPSEEK_V3_2)).toEqual(true)
  }

  @Test
  fun `should rate limit CLAUDE_SONNET_4_5 at configured rate`() {
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_SONNET_4_5)).toEqual(true)
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_SONNET_4_5)).toEqual(false)
    clock.advance((MILLISECONDS_PER_MINUTE / AiModel.CLAUDE_SONNET_4_5.rateLimitPerMinute) + RATE_LIMIT_BUFFER_MS)
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_SONNET_4_5)).toEqual(true)
  }

  @Test
  fun `should rate limit CLAUDE_OPUS_4_5 at configured rate`() {
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_OPUS_4_5)).toEqual(true)
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_OPUS_4_5)).toEqual(false)
    clock.advance((MILLISECONDS_PER_MINUTE / AiModel.CLAUDE_OPUS_4_5.rateLimitPerMinute) + RATE_LIMIT_BUFFER_MS)
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_OPUS_4_5)).toEqual(true)
  }

  @Test
  fun `should track rate limits independently for each model`() {
    expect(circuitBreaker.tryAcquireForModel(AiModel.GEMINI_3_FLASH_PREVIEW)).toEqual(true)
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_OPUS_4_5)).toEqual(true)
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_SONNET_4_5)).toEqual(true)
    expect(circuitBreaker.tryAcquireForModel(AiModel.DEEPSEEK_V3_2)).toEqual(true)
    expect(circuitBreaker.tryAcquireForModel(AiModel.GEMINI_3_FLASH_PREVIEW)).toEqual(false)
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_OPUS_4_5)).toEqual(false)
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_SONNET_4_5)).toEqual(false)
    expect(circuitBreaker.tryAcquireForModel(AiModel.DEEPSEEK_V3_2)).toEqual(false)
  }

  @Test
  fun `should return correct wait time for CLAUDE_OPUS_4_5`() {
    circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_OPUS_4_5)
    val waitTime = circuitBreaker.getWaitTimeMsForModel(AiModel.CLAUDE_OPUS_4_5)
    val expectedMs = MILLISECONDS_PER_MINUTE / AiModel.CLAUDE_OPUS_4_5.rateLimitPerMinute
    expect(waitTime).toEqual(expectedMs)
  }

  @Test
  fun `should select model by sector tier`() {
    val tier0 = circuitBreaker.selectModelByTier(0)
    val tier1 = circuitBreaker.selectModelByTier(1)
    val tier2 = circuitBreaker.selectModelByTier(2)
    val tier3 = circuitBreaker.selectModelByTier(3)
    val tier4 = circuitBreaker.selectModelByTier(4)
    expect(tier0.model).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
    expect(tier1.model).toEqual(AiModel.CLAUDE_OPUS_4_5)
    expect(tier2.model).toEqual(AiModel.CLAUDE_SONNET_4_5)
    expect(tier3.model).toEqual(AiModel.GEMINI_2_5_FLASH)
    expect(tier4.model).toEqual(AiModel.DEEPSEEK_V3_2)
  }

  @Test
  fun `should return fallback model for invalid tier`() {
    val invalidTier = circuitBreaker.selectModelByTier(99)
    expect(invalidTier.model).toEqual(AiModel.CLAUDE_OPUS_4_5)
  }

  @Test
  fun `should reset all model rate limits`() {
    circuitBreaker.tryAcquireForModel(AiModel.GEMINI_3_FLASH_PREVIEW)
    circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_OPUS_4_5)
    circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_SONNET_4_5)
    circuitBreaker.tryAcquireForModel(AiModel.DEEPSEEK_V3_2)
    circuitBreaker.resetRateLimits()
    expect(circuitBreaker.tryAcquireForModel(AiModel.GEMINI_3_FLASH_PREVIEW)).toEqual(true)
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_OPUS_4_5)).toEqual(true)
    expect(circuitBreaker.tryAcquireForModel(AiModel.CLAUDE_SONNET_4_5)).toEqual(true)
    expect(circuitBreaker.tryAcquireForModel(AiModel.DEEPSEEK_V3_2)).toEqual(true)
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
