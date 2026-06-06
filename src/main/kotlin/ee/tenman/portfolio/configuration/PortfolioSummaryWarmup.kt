package ee.tenman.portfolio.configuration

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class PortfolioSummaryWarmup(
  private val environment: Environment,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @EventListener(ApplicationReadyEvent::class)
  fun warmUp() {
    val startTime = System.currentTimeMillis()
    runCatching {
      val baseUrl = "http://localhost:${resolvePort()}"
      val client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build()
      repeat(WARMUP_ROUNDS) {
        WARMUP_PATHS.forEach { path ->
          client.send(
            HttpRequest
              .newBuilder(URI.create(baseUrl + path))
              .timeout(REQUEST_TIMEOUT)
              .GET()
              .build(),
            HttpResponse.BodyHandlers.discarding(),
          )
        }
      }
    }.onSuccess {
      log.info("Warmed portfolio summary path in ${System.currentTimeMillis() - startTime} ms")
    }.onFailure {
      log.warn("Failed to warm portfolio summary path", it)
    }
  }

  private fun resolvePort(): String =
    environment.getProperty("local.server.port")
      ?: environment.getProperty("server.port")
      ?: DEFAULT_PORT

  companion object {
    private const val DEFAULT_PORT = "8081"
    private const val WARMUP_ROUNDS = 2
    private val REQUEST_TIMEOUT = Duration.ofSeconds(30)
    private val WARMUP_PATHS =
      listOf(
        "/api/transactions/platforms",
        "/api/portfolio-summary/historical?page=0&size=186",
        "/api/portfolio-summary/current",
      )
  }
}
