package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.service.transaction.TransactionService
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
  private val transactionService: TransactionService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @EventListener(ApplicationReadyEvent::class)
  fun warmUp() {
    val startTime = System.currentTimeMillis()
    runCatching {
      val baseUrl = "http://localhost:${resolvePort()}"
      val client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build()
      val paths = warmupPaths()
      repeat(WARMUP_ROUNDS) {
        paths.forEach { path ->
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

  fun warmupPaths(): List<String> {
    val platformQuery = transactionService.getDistinctPlatforms().joinToString("&") { "platforms=${it.name}" }
    if (platformQuery.isEmpty()) return BASE_PATHS
    return BASE_PATHS +
      listOf(
        "/api/portfolio-summary/historical?page=0&size=$HISTORICAL_PAGE_SIZE&$platformQuery",
        "/api/portfolio-summary/current?$platformQuery",
      )
  }

  private fun resolvePort(): String =
    environment.getProperty("local.server.port")
      ?: environment.getProperty("server.port")
      ?: DEFAULT_PORT

  companion object {
    private const val DEFAULT_PORT = "8081"
    private const val WARMUP_ROUNDS = 2
    private const val HISTORICAL_PAGE_SIZE = 186
    private val REQUEST_TIMEOUT = Duration.ofSeconds(30)
    private val BASE_PATHS =
      listOf(
        "/api/transactions/platforms",
        "/api/portfolio-summary/historical?page=0&size=$HISTORICAL_PAGE_SIZE",
        "/api/portfolio-summary/current",
      )
  }
}
