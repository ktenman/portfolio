package ee.tenman.portfolio.controller

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory

@RestController
@RequestMapping("/api/health")
class HealthController(
  private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
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
    )
}
