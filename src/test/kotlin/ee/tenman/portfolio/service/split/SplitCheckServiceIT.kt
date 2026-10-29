package ee.tenman.portfolio.service.split

import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.openrouter.OpenRouterCircuitBreaker
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.InstrumentSplitRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.wiremock.spring.InjectWireMock
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@IntegrationTest
@TestPropertySource(properties = ["openrouter.api-key=test-api-key", "openrouter.url=http://localhost:\${wiremock.server.port}"])
class SplitCheckServiceIT {
  @Resource private lateinit var splitCheckService: SplitCheckService

  @Resource private lateinit var splitMetricsService: SplitMetricsService

  @Resource private lateinit var meterRegistry: MeterRegistry

  @Resource private lateinit var instrumentSplitRepository: InstrumentSplitRepository

  @Resource private lateinit var instrumentRepository: InstrumentRepository

  @Resource private lateinit var portfolioTransactionRepository: PortfolioTransactionRepository

  @Resource private lateinit var circuitBreaker: OpenRouterCircuitBreaker

  @Resource private lateinit var objectMapper: ObjectMapper

  @Resource private lateinit var clock: Clock

  @InjectWireMock private lateinit var wireMockServer: WireMockServer

  private val effective by lazy { LocalDate.now(clock).plusDays(25) }

  @BeforeEach
  fun setUp() {
    wireMockServer.resetAll()
    circuitBreaker.reset()
    hold("BNKE:PAR:EUR", "Amundi Euro Stoxx Banks UCITS ETF Acc", ProviderName.TRADING212, "10")
    hold("VUAA:GER:EUR", "Vanguard S&P 500 UCITS ETF (USD) Accumulating", ProviderName.LIGHTYEAR, "3")
    hold("BTCEUR", "Bitcoin", ProviderName.BINANCE, "0.1")
    hold("LSMC:GER:EUR", "Amundi MSCI Semiconductors UCITS ETF", ProviderName.LIGHTYEAR, "5", sold = "5")
    wireMockServer.stubFor(
      get(urlEqualTo("/grounding-api-redirect/bnke"))
        .willReturn(aResponse().withStatus(302).withHeader("Location", "${wireMockServer.baseUrl()}/notice/9804833")),
    )
    wireMockServer.stubFor(get(urlEqualTo("/notice/9804833")).willReturn(ok("Avis aux actionnaires")))
  }

  @Test
  fun `should store the announced split with the address its source redirects to`() {
    answer(bnke = announced())
    splitCheckService.check()
    expect(stored()).toContainExactly(
      "BNKE:PAR:EUR 1:80 $effective ${wireMockServer.baseUrl()}/notice/9804833 2026-10-02",
    )
  }

  @Test
  fun `should store nothing when no split is announced`() {
    answer(bnke = none("BNKE:PAR:EUR"))
    splitCheckService.check()
    expect(stored()).toBeEmpty()
  }

  @Test
  fun `should export the announced split once for the alert`() {
    answer(bnke = announced())
    splitCheckService.check()
    expect(exported()).toContainExactly("BNKE:PAR:EUR 1:80 $effective")
  }

  @Test
  fun `should not store the same split again on the next run`() {
    answer(bnke = announced())
    repeat(2) {
      circuitBreaker.resetRateLimits()
      splitCheckService.check()
    }
    expect(stored()).toHaveSize(1)
  }

  @Test
  fun `should ask about held instruments other than Binance ones in one request`() {
    answer(bnke = none("BNKE:PAR:EUR"))
    splitCheckService.check()
    val body = wireMockServer.findAll(postRequestedFor(urlEqualTo("/chat/completions"))).single().bodyAsString
    expect(body)
      .toContain("BNKE:PAR:EUR | Amundi Euro Stoxx Banks UCITS ETF Acc", "VUAA:GER:EUR", "${LocalDate.now(clock)}")
      .notToContain("BTCEUR", "LSMC")
  }

  @Test
  fun `should fail when OpenRouter gives no answer`() {
    wireMockServer.stubFor(post(urlEqualTo("/chat/completions")).willReturn(serverError()))
    expect { splitCheckService.check() }.toThrow<IllegalStateException>()
  }

  private fun stored(): List<String> =
    instrumentSplitRepository.findPending(LocalDate.now(clock)).map {
      "${it.instrument.symbol} ${it.ratio} ${it.effectiveDate} ${it.sourceUrl} ${it.publishedDate}"
    }

  private fun exported(): List<String> {
    splitMetricsService.refresh()
    return meterRegistry.find("portfolio.split.announced").gauges().map {
      "${it.id.getTag("instrument")} ${it.id.getTag("ratio")} ${it.id.getTag("effective")}"
    }
  }

  private fun announced(): String =
    """{"symbol":"BNKE:PAR:EUR","announced":true,"ratio":"1:80","effectiveDate":"$effective",""" +
      """"sourceUrl":"${wireMockServer.baseUrl()}/grounding-api-redirect/bnke","publishedDate":"2026-10-02"}"""

  private fun answer(bnke: String) {
    val content = "[$bnke,${none("VUAA:GER:EUR")}]"
    val body = objectMapper.writeValueAsString(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))))
    wireMockServer.stubFor(post(urlEqualTo("/chat/completions")).willReturn(okJson(body)))
  }

  private fun hold(
    symbol: String,
    name: String,
    provider: ProviderName,
    quantity: String,
    sold: String? = null,
  ) {
    val instrument = instrumentRepository.save(Instrument(symbol, name, "ETF", "EUR", BigDecimal.TEN, provider))
    val trades = listOfNotNull(TransactionType.BUY to quantity, sold?.let { TransactionType.SELL to it })
    trades.forEach { (type, amount) ->
      portfolioTransactionRepository.save(
        PortfolioTransaction(instrument, type, BigDecimal(amount), BigDecimal.TEN, LocalDate.of(2026, 1, 5), Platform.LIGHTYEAR),
      )
    }
  }

  private fun none(symbol: String) =
    """{"symbol":"$symbol","announced":false,"ratio":null,"effectiveDate":null,"sourceUrl":null,"publishedDate":null}"""
}
