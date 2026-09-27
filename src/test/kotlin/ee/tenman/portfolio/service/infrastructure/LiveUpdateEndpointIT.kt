package ee.tenman.portfolio.service.infrastructure

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.LiveUpdate
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.HttpURLConnection
import java.net.URI

@IntegrationTest
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["management.server.port=0", "management.endpoints.web.exposure.include=health,info,prometheus"],
)
class LiveUpdateEndpointIT {
  @LocalServerPort private var port: Int = 0

  @Resource private lateinit var liveUpdates: LiveUpdateService

  @Test
  fun `should announce the connection as soon as a client subscribes`() {
    expect(subscribe { it.next() }).toEqual(":connected")
  }

  @Test
  fun `should stream the topic of a published update`() {
    val event =
      subscribe { lines ->
        lines.next()
        liveUpdates.publish(LiveUpdate.PRICES)
        lines.next()
      }
    expect(event).toEqual("event:PRICES")
  }

  private fun <T> subscribe(read: (Iterator<String>) -> T): T {
    val connection = URI("http://localhost:$port/api/live-updates").toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 3000
    connection.readTimeout = 5000
    return connection.inputStream.bufferedReader().use { reader ->
      read(reader.lineSequence().filter { it.isNotBlank() }.iterator())
    }
  }
}
