package ee.tenman.portfolio.openrouter

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.notContaining
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.AiPurpose
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

    val result = openRouterClient.classifyWithModel(AiPurpose.SECTOR, "Classify Apple Inc")

    expect(result).notToEqualNull()
    expect(result?.content).toEqual("Technology")
    expect(result?.model).toEqual(AiModel.primarySectorModel())
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.CLOSED)
  }

  @Test
  fun `should switch to fallback model after failures exceed threshold`() {
    stubOpenRouterError()

    repeat(3) {
      circuitBreaker.resetRateLimits()
      openRouterClient.classifyWithModel(AiPurpose.SECTOR, "Classify company")
    }

    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.OPEN)
    expect(circuitBreaker.isUsingFallback()).toEqual(true)
  }

  @Test
  fun `should handle server error and record failure`() {
    stubOpenRouterError()

    val result = openRouterClient.classifyWithModel(AiPurpose.SECTOR, "Test prompt")

    expect(result).toEqual(null)
    wireMockServer.verify(1, postRequestedFor(urlEqualTo("/chat/completions")))
  }

  @Test
  fun `should recover from half-open state on success`() {
    stubOpenRouterError()
    repeat(3) {
      circuitBreaker.resetRateLimits()
      openRouterClient.classifyWithModel(AiPurpose.SECTOR, "Classify company")
    }
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.OPEN)

    circuitBreaker.transitionToHalfOpenState()
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.HALF_OPEN)

    wireMockServer.resetAll()
    stubOpenRouterSuccess(createSuccessResponse("Finance"))
    circuitBreaker.resetRateLimits()

    val result = openRouterClient.classifyWithModel(AiPurpose.SECTOR, "Classify bank")

    expect(result).notToEqualNull()
    expect(circuitBreaker.getState()).toEqual(CircuitBreaker.State.CLOSED)
  }

  @Test
  fun `should use fallback model when circuit is open`() {
    stubOpenRouterError()
    repeat(3) {
      circuitBreaker.resetRateLimits()
      openRouterClient.classifyWithModel(AiPurpose.SECTOR, "Classify company")
    }
    expect(circuitBreaker.isUsingFallback()).toEqual(true)

    wireMockServer.resetAll()
    stubOpenRouterSuccess(createSuccessResponse("Healthcare"))
    circuitBreaker.resetRateLimits()

    val result = openRouterClient.classifyWithModel(AiPurpose.SECTOR, "Classify hospital")

    expect(result).notToEqualNull()
    expect(result?.content).toEqual("Healthcare")
    expect(result?.model).toEqual(AiModel.GEMINI_4_ARGON)
  }

  @Test
  fun `should send system prompt and strict json schema when completing`() {
    wireMockServer.stubFor(
      post(urlEqualTo("/chat/completions"))
        .withRequestBody(matchingJsonPath("$.model", equalTo("google/gemini-4-argon")))
        .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
        .withRequestBody(matchingJsonPath("$.messages[1].content", equalTo("Saade algab 11:59.")))
        .withRequestBody(matchingJsonPath("$.response_format.json_schema.strict", equalTo("true")))
        .withRequestBody(matchingJsonPath("$.provider.require_parameters", equalTo("true")))
        .withRequestBody(matchingJsonPath("$.provider.order[0]", equalTo("google-ai-studio/flex")))
        .willReturn(okJson(createSuccessResponse("{\"hosts\":[]}"))),
    )
    val result =
      openRouterClient.complete(
        AiPurpose.RADIO_OUTLINE,
        AiModel.GEMINI_4_ARGON,
        "Leia saade.",
        "Saade algab 11:59.",
        mapOf("type" to "object"),
      )
    expect(result).toEqual("{\"hosts\":[]}")
  }

  @Test
  fun `should complete on OpenAI's flex tier without a temperature`() {
    wireMockServer.stubFor(
      post(urlEqualTo("/chat/completions"))
        .withRequestBody(matchingJsonPath("$.model", equalTo("openai/gpt-6.1-terra")))
        .withRequestBody(matchingJsonPath("$.provider.order[0]", equalTo("openai/flex")))
        .withRequestBody(notContaining("temperature"))
        .willReturn(okJson(createSuccessResponse("{\"hosts\":[]}"))),
    )
    val result =
      openRouterClient.complete(
        AiPurpose.RADIO_OUTLINE,
        AiModel.GPT_6_1_TERRA,
        "Leia saade.",
        "Saade algab 11:59.",
        mapOf("type" to "object"),
      )
    expect(result).toEqual("{\"hosts\":[]}")
  }

  @Test
  fun `should search the web natively through Vertex without falling back to another provider`() {
    wireMockServer.stubFor(
      post(urlEqualTo("/chat/completions"))
        .withRequestBody(matchingJsonPath("$.model", equalTo("google/gemini-4-argon")))
        .withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("Kas BNKE jaguneb?")))
        .withRequestBody(matchingJsonPath("$.plugins[0].id", equalTo("web")))
        .withRequestBody(matchingJsonPath("$.plugins[0].engine", equalTo("native")))
        .withRequestBody(matchingJsonPath("$.provider.order[0]", equalTo("google-vertex")))
        .withRequestBody(matchingJsonPath("$.provider.allow_fallbacks", equalTo("false")))
        .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("32000")))
        .willReturn(okJson(createSuccessResponse("[]"))),
    )
    expect(openRouterClient.search(AiPurpose.SPLIT_CHECK, AiModel.GEMINI_4_ARGON, "Kas BNKE jaguneb?")).toEqual("[]")
  }

  @Test
  fun `should return null when completion answers with an error body`() {
    stubOpenRouterSuccess("{\"error\":{\"message\":\"upstream\"}}")
    val result =
      openRouterClient.complete(
        AiPurpose.RADIO_OUTLINE,
        AiModel.GEMINI_4_ARGON,
        "Leia saade.",
        "Saade.",
        mapOf("type" to "object"),
      )
    expect(result).toEqual(null)
  }

  @Test
  fun `should leave response format out of classification requests`() {
    wireMockServer.stubFor(
      post(urlEqualTo("/chat/completions"))
        .withRequestBody(notContaining("response_format"))
        .withRequestBody(notContaining("provider"))
        .willReturn(okJson(createSuccessResponse("Technology"))),
    )
    val result = openRouterClient.classify(AiPurpose.SECTOR, "Classify Apple Inc")
    expect(result).toEqual("Technology")
  }

  @Test
  fun `should send the reasoning effort of the model that answers`() {
    wireMockServer.stubFor(
      post(urlEqualTo("/chat/completions"))
        .withRequestBody(matchingJsonPath("$.model", equalTo("deepseek/deepseek-v4-pro")))
        .withRequestBody(matchingJsonPath("$.reasoning.effort", equalTo("none")))
        .willReturn(okJson(createSuccessResponse("Finance"))),
    )
    val result = openRouterClient.classifyWithCascadingFallback(AiPurpose.SECTOR, "Põhja Pank", AiModel.DEEPSEEK_V4_PRO)
    expect(result?.content).toEqual("Finance")
  }

  @Test
  fun `should leave reasoning out of a request to a model with no effort set`() {
    wireMockServer.stubFor(
      post(urlEqualTo("/chat/completions"))
        .withRequestBody(notContaining("reasoning"))
        .willReturn(okJson(createSuccessResponse("Finance"))),
    )
    val result = openRouterClient.classify(AiPurpose.SECTOR, "Põhja Pank")
    expect(result).toEqual("Finance")
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
