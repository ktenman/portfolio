package ee.tenman.portfolio.veego

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.VEEGO_TAX_CACHE
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal

@IntegrationTest
@TestPropertySource(properties = ["veego.url=http://localhost:\${wiremock.server.port}"])
class VeegoServiceIT {
  @Resource
  private lateinit var veegoService: VeegoService

  @Resource
  private lateinit var cacheManager: CacheManager

  @BeforeEach
  fun resetWireMock() {
    WireMock.reset()
  }

  @Test
  fun `should serve second call for same plate from cache without invoking the client`() {
    stubVeegoSuccess("471GWJ")

    val first = veegoService.getTaxInfo("471GWJ")
    awaitCachePopulated("471GWJ")
    val second = veegoService.getTaxInfo("471GWJ")

    expect(first.annualTax).notToEqualNull().toEqualNumerically(BigDecimal("369.03"))
    expect(second.annualTax).notToEqualNull().toEqualNumerically(BigDecimal("369.03"))
    verify(exactly(1), postRequestedFor(urlPathEqualTo("/vehicles/471GWJ/tax")))
  }

  private fun awaitCachePopulated(plateNumber: String) {
    val cache = cacheManager.getCache(VEEGO_TAX_CACHE) ?: error("$VEEGO_TAX_CACHE not configured")
    val deadline = System.currentTimeMillis() + 2_000L
    while (System.currentTimeMillis() < deadline) {
      if (cache.get(plateNumber) != null) return
      Thread.sleep(10)
    }
    error("Cache $VEEGO_TAX_CACHE was not populated for $plateNumber within 2s")
  }

  @Test
  fun `should not cache error responses so a later success can populate the cache`() {
    stubVeegoError("999ZZZ")

    val failure = veegoService.getTaxInfo("999ZZZ")
    expect(failure.error).notToEqualNull()

    stubVeegoSuccess("999ZZZ")
    val success = veegoService.getTaxInfo("999ZZZ")

    expect(success.error).toEqual(null)
    expect(success.make).toEqual("Bentley")
  }

  private fun stubVeegoSuccess(plateNumber: String) {
    stubFor(
      post(urlPathEqualTo("/vehicles/$plateNumber/tax"))
        .withRequestBody(equalToJson("""{"reg":"$plateNumber"}"""))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(
              """
              {
                "annual_tax": 369.03,
                "registration_tax": 2335.8,
                "make": "Bentley",
                "model": "Mulsanne",
                "year": 2011,
                "group": "Passenger car",
                "co2": 393,
                "fuel": "Petrol",
                "weight": 3089
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  private fun stubVeegoError(plateNumber: String) {
    stubFor(
      post(urlPathEqualTo("/vehicles/$plateNumber/tax"))
        .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")),
    )
  }
}
