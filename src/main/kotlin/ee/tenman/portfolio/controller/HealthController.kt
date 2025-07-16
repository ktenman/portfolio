package ee.tenman.portfolio.controller

import ee.tenman.portfolio.webdriver.FirefoxDriverService
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/health")
class HealthController(
  private val firefoxDriverService: FirefoxDriverService,
  private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
  @GetMapping("/firefox/verify")
  fun verifyFirefoxDriver(): WebDriverVerificationResponse {
    val startTime = System.currentTimeMillis()
    val isHealthy = firefoxDriverService.verifyDriver()
    val duration = System.currentTimeMillis() - startTime

    return WebDriverVerificationResponse(
      driver = "Firefox",
      healthy = isHealthy,
      message = if (isHealthy) "Firefox driver is working properly" else "Firefox driver verification failed",
      durationMs = duration,
      timestamp = LocalDateTime.now(),
    )
  }

  @GetMapping("/firefox/status")
  fun getFirefoxDriverStatus(): WebDriverStatusResponse =
    WebDriverStatusResponse(
      driver = "Firefox",
      poolHealthy = firefoxDriverService.isHealthy(),
      timestamp = LocalDateTime.now(),
    )

  @GetMapping("/threads")
  fun getThreadInfo(): Map<String, Any> {
    val threadBean = ManagementFactory.getThreadMXBean()
    val runtimeBean = ManagementFactory.getRuntimeMXBean()

    return mapOf(
      "activeThreadCount" to Thread.activeCount(),
      "totalStartedThreadCount" to threadBean.totalStartedThreadCount,
      "peakThreadCount" to threadBean.peakThreadCount,
      "daemonThreadCount" to threadBean.daemonThreadCount,
      "deadlockedThreads" to (threadBean.findDeadlockedThreads()?.size ?: 0),
      "uptime" to runtimeBean.uptime,
      "availableProcessors" to Runtime.getRuntime().availableProcessors(),
    )
  }

  @GetMapping("/circuit-breakers")
  fun getCircuitBreakerInfo(): Map<String, Any> =
    circuitBreakerRegistry.allCircuitBreakers.associate { circuitBreaker ->
      circuitBreaker.name to
        mapOf(
          "state" to circuitBreaker.state.toString(),
          "failureRate" to circuitBreaker.metrics.failureRate,
          "numberOfBufferedCalls" to circuitBreaker.metrics.numberOfBufferedCalls,
          "numberOfFailedCalls" to circuitBreaker.metrics.numberOfFailedCalls,
          "numberOfSuccessfulCalls" to circuitBreaker.metrics.numberOfSuccessfulCalls,
          "numberOfNotPermittedCalls" to circuitBreaker.metrics.numberOfNotPermittedCalls,
        )
    }

  @GetMapping("/memory")
  fun getMemoryInfo(): Map<String, Any> {
    val runtime = Runtime.getRuntime()
    val mb = 1024 * 1024

    return mapOf(
      "totalMemoryMB" to runtime.totalMemory() / mb,
      "freeMemoryMB" to runtime.freeMemory() / mb,
      "maxMemoryMB" to runtime.maxMemory() / mb,
      "usedMemoryMB" to (runtime.totalMemory() - runtime.freeMemory()) / mb,
    )
  }

  @GetMapping("/system")
  fun getSystemInfo(): Map<String, Any> =
    mapOf(
      "threads" to getThreadInfo(),
      "circuitBreakers" to getCircuitBreakerInfo(),
      "memory" to getMemoryInfo(),
      "firefox" to getFirefoxDriverStatus(),
    )
}

data class WebDriverVerificationResponse(
  val driver: String,
  val healthy: Boolean,
  val message: String,
  val durationMs: Long,
  val timestamp: LocalDateTime,
)

data class WebDriverStatusResponse(
  val driver: String,
  val poolHealthy: Boolean,
  val timestamp: LocalDateTime,
)
