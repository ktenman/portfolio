package ee.tenman.portfolio.service.monitoring

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@IntegrationTest
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["management.server.port=0", "management.endpoints.web.exposure.include=health,info,prometheus"],
)
class MonitoringEndpointIT {
  @LocalServerPort private var applicationPort: Int = 0

  @LocalManagementPort private var managementPort: Int = 0

  @Resource private lateinit var metrics: CollectionMetricsService

  @Resource private lateinit var stream: CollectionStreamService

  @Test
  fun `should expose complete collection metrics on the management listener`() {
    metrics.refresh()
    val response = get(managementPort, "/actuator/prometheus")
    expect(response.statusCode()).toEqual(200)
    expect(
      response.body(),
    ).toContain(
      "portfolio_collection_inventory_ready 1.0",
      "portfolio_collection_operation_info",
      "portfolio_collection_deadline_timestamp_seconds",
    )
  }

  @Test
  fun `should keep collection metrics off the application listener`() {
    expect(get(applicationPort, "/actuator/prometheus").statusCode()).toEqual(404)
  }

  @Test
  fun `should stream the collection status as soon as a client subscribes`() {
    metrics.refresh()
    expect(subscribe { it.next() }).toContain("\"key\":\"LIGHTYEAR_PRICES\"")
  }

  @Test
  fun `should stream the collection status again when it is published`() {
    val event =
      subscribe { events ->
        events.next()
        stream.publish()
        events.next()
      }
    expect(event).toContain("\"key\":\"LIGHTYEAR_PRICES\"")
  }

  private fun <T> subscribe(read: (Iterator<String>) -> T): T {
    val connection =
      URI(
      "http://localhost:$applicationPort/api/monitoring/collections/stream",
    ).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 3000
    connection.readTimeout = 5000
    return connection.inputStream.bufferedReader().use { reader ->
      read(reader.lineSequence().filter { it.startsWith("data:") }.iterator())
    }
  }

  private fun get(
    port: Int,
    path: String,
  ): HttpResponse<String> =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().use { client ->
      val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(5)).build()
      client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
