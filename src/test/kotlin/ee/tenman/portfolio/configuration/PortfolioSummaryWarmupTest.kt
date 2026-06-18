package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.sun.net.httpserver.HttpServer
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.service.transaction.TransactionService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

class PortfolioSummaryWarmupTest {
  private val requestedPaths = ConcurrentLinkedQueue<String>()
  private val transactionService = mockk<TransactionService>()
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
  fun `should warm up platform filtered summary endpoints the frontend requests on application ready`() {
    every { transactionService.getDistinctPlatforms() } returns listOf(Platform.LIGHTYEAR, Platform.TRADING212)
    val environment = MockEnvironment().withProperty("server.port", server.address.port.toString())
    val warmup = PortfolioSummaryWarmup(environment, transactionService)
    warmup.warmUp()
    expect(requestedPaths.toSet()).toContain(
      "/api/portfolio-summary/historical?page=0&size=186&platforms=LIGHTYEAR&platforms=TRADING212",
      "/api/portfolio-summary/current?platforms=LIGHTYEAR&platforms=TRADING212",
    )
  }

  @Test
  fun `should warm up unfiltered summary endpoints alongside the filtered ones`() {
    every { transactionService.getDistinctPlatforms() } returns listOf(Platform.LIGHTYEAR)
    val environment = MockEnvironment().withProperty("server.port", server.address.port.toString())
    val warmup = PortfolioSummaryWarmup(environment, transactionService)
    warmup.warmUp()
    expect(requestedPaths.toSet()).toContain(
      "/api/transactions/platforms",
      "/api/portfolio-summary/historical?page=0&size=186",
      "/api/portfolio-summary/current",
    )
  }

  @Test
  fun `should warm up only unfiltered endpoints when no platforms exist`() {
    every { transactionService.getDistinctPlatforms() } returns emptyList()
    val environment = MockEnvironment().withProperty("server.port", server.address.port.toString())
    val warmup = PortfolioSummaryWarmup(environment, transactionService)
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
    every { transactionService.getDistinctPlatforms() } returns listOf(Platform.LIGHTYEAR)
    val environment = MockEnvironment().withProperty("server.port", "1")
    val warmup = PortfolioSummaryWarmup(environment, transactionService)
    warmup.warmUp()
    expect(requestedPaths.size).toEqual(0)
  }
}
