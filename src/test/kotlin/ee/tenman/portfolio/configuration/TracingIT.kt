package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.verbs.expect
import com.sun.net.httpserver.HttpServer
import jakarta.annotation.Resource
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.time.Duration

@IntegrationTest
class TracingIT {
  @Resource
  private lateinit var mockMvc: MockMvc

  @Test
  fun `should export the request and its database query to the OTLP endpoint`() {
    mockMvc.perform(get("/api/transactions"))
    await().atMost(Duration.ofSeconds(30)).untilAsserted {
      expect(exported.toString(Charsets.ISO_8859_1)).toContain("http get /api/transactions", "jdbc.query")
    }
  }

  companion object {
    private val exported = ByteArrayOutputStream()

    private val receiver =
      HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/v1/traces") { exchange ->
          exchange.requestBody.use { it.transferTo(exported) }
          exchange.sendResponseHeaders(200, -1)
          exchange.close()
        }
        start()
      }

    @JvmStatic
    @DynamicPropertySource
    fun endpoint(registry: DynamicPropertyRegistry) {
      registry.add("management.opentelemetry.tracing.export.otlp.endpoint") {
        "http://localhost:${receiver.address.port}/v1/traces"
      }
    }

    @JvmStatic
    @AfterAll
    fun stop() {
      receiver.stop(0)
    }
  }
}
