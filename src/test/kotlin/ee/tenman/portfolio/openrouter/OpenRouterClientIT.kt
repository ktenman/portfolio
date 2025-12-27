package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.AiModel
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.wiremock.spring.InjectWireMock
import tools.jackson.databind.ObjectMapper

@IntegrationTest
@TestPropertySource(properties = ["openrouter.api-key=test-api-key", "openrouter.url=http://localhost:\${wiremock.server.port}"])
class OpenRouterClientIT {
  @Resource
  private lateinit var openRouterClient: OpenRouterClient

  @Resource
  private lateinit var circuitBreaker: OpenRouterCircuitBreaker

  @Resource
  private lateinit var objectMapper: ObjectMapper

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  @BeforeEach
  fun setUp() {
    wireMockServer.resetAll()
    circuitBreaker.reset()
  }

  @Test
  fun `should successfully classify with primary model`() {
    val responseBody = createSuccessResponse("Technology")
    stubOpenRouterSuccess(responseBody)

    val result = openRouterClient.classifyWithModel("Classify Apple Inc")

    expect(result).notToEqualNull()
    expect(result?.content).toEqual("Technology")
    expect(result?.model).toEqual(AiModel.GEMINI_3_FLASH_PREVIEW)
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.CLOSED)
  }

  @Test
  fun `should switch to fallback model after failures exceed threshold`() {
    stubOpenRouterError()

    repeat(3) {
      circuitBreaker.resetRateLimits()
      openRouterClient.classifyWithModel("Classify company")
    }

    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.OPEN)
    expect(circuitBreaker.isUsingFallback()).toEqual(true)
  }

  @Test
  fun `should handle server error and record failure`() {
    stubOpenRouterError()

    val result = openRouterClient.classifyWithModel("Test prompt")

    expect(result).toEqual(null)
    wireMockServer.verify(1, postRequestedFor(urlEqualTo("/chat/completions")))
  }

  @Test
  fun `should recover from half-open state on success`() {
    stubOpenRouterError()
    repeat(3) {
      circuitBreaker.resetRateLimits()
      openRouterClient.classifyWithModel("Classify company")
    }
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.OPEN)

    circuitBreaker.transitionToHalfOpenState()
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.HALF_OPEN)

    wireMockServer.resetAll()
    stubOpenRouterSuccess(createSuccessResponse("Finance"))
    circuitBreaker.resetRateLimits()

    val result = openRouterClient.classifyWithModel("Classify bank")

    expect(result).notToEqualNull()
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.CLOSED)
  }

  @Test
  fun `should use fallback model when circuit is open`() {
    stubOpenRouterError()
    repeat(3) {
      circuitBreaker.resetRateLimits()
      openRouterClient.classifyWithModel("Classify company")
    }
    expect(circuitBreaker.isUsingFallback()).toEqual(true)

    wireMockServer.resetAll()
    stubOpenRouterSuccess(createSuccessResponse("Healthcare"))
    circuitBreaker.resetRateLimits()

    val result = openRouterClient.classifyWithModel("Classify hospital")

    expect(result).notToEqualNull()
    expect(result?.content).toEqual("Healthcare")
    expect(result?.model).toEqual(AiModel.CLAUDE_OPUS_4_5)
  }

  private fun createSuccessResponse(content: String): String =
    objectMapper.writeValueAsString(
      mapOf(
        "choices" to
          listOf(
            mapOf(
              "message" to
                mapOf(
                  "content" to content,
                ),
            ),
          ),
      ),
    )

  private fun stubOpenRouterSuccess(responseBody: String) {
    wireMockServer.stubFor(
      post(urlEqualTo("/chat/completions"))
        .willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withBody(responseBody),
        ),
    )
  }

  private fun stubOpenRouterError() {
    wireMockServer.stubFor(
      post(urlEqualTo("/chat/completions"))
        .willReturn(
          aResponse()
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()),
        ),
    )
  }
}
