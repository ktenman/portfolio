package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

class PortfolioSummaryWarmupTest {
  private val requestedPaths = ConcurrentLinkedQueue<String>()
  private lateinit var server: HttpServer

  @BeforeEach
  fun startStubServer() {
    server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
    server.createContext("/") { exchange ->
      requestedPaths.add(exchange.requestURI.toString())
      exchange.sendResponseHeaders(200, -1)
      exchange.close()
    }
    server.start()
  }

  @AfterEach
  fun stopStubServer() {
    server.stop(0)
  }

  @Test
  fun `should warm up every portfolio summary endpoint over http on application ready`() {
    val environment = MockEnvironment().withProperty("server.port", server.address.port.toString())
    val warmup = PortfolioSummaryWarmup(environment)
    warmup.warmUp()
    expect(requestedPaths.toSet()).toEqual(
      setOf(
        "/api/transactions/platforms",
        "/api/portfolio-summary/historical?page=0&size=186",
        "/api/portfolio-summary/current",
      ),
    )
  }

  @Test
  fun `should not propagate failures when the warmup target is unreachable`() {
    val environment = MockEnvironment().withProperty("server.port", "1")
    val warmup = PortfolioSummaryWarmup(environment)
    warmup.warmUp()
    expect(requestedPaths.size).toEqual(0)
  }
}
