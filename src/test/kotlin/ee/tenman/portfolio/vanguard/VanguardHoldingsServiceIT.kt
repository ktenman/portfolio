package ee.tenman.portfolio.vanguard

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqualNumerically
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.GicsIndustry
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.wiremock.spring.InjectWireMock
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@IntegrationTest
class VanguardHoldingsServiceIT {
  @Resource
  private lateinit var vanguardHoldingsService: VanguardHoldingsService

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  @BeforeEach
  fun setUp() {
    wireMockServer.resetAll()
  }

  @Test
  fun `should post the fund port id to the Vanguard GraphQL endpoint`() {
    stubBothPages()

    vanguardHoldingsService.fetchHoldings(PORT_ID)

    wireMockServer.verify(postRequestedFor(urlPathEqualTo(GRAPHQL_PATH)).withRequestBody(containing("""["E161"]""")))
  }

  @Test
  fun `should send the key of the previous page back to Vanguard`() {
    stubBothPages()

    vanguardHoldingsService.fetchHoldings(PORT_ID)

    wireMockServer.verify(postRequestedFor(urlPathEqualTo(GRAPHQL_PATH)).withRequestBody(containing(""""page-2-key"""")))
  }

  @Test
  fun `should decode weights that carry more decimals than the merged total`() {
    stubBothPages()

    val alphabet = vanguardHoldingsService.fetchHoldings(PORT_ID).holdings.first { it.name == "Alphabet Inc" }

    expect(alphabet.weight.setScale(5, RoundingMode.HALF_UP)).toEqualNumerically(BigDecimal("3.02683"))
  }

  @Test
  fun `should decode the effective date of a fund snapshot`() {
    stubBothPages()

    val snapshot = vanguardHoldingsService.fetchHoldings(PORT_ID)

    expect(snapshot.effectiveDate).toEqual(LocalDate.of(2026, 8, 31))
  }

  @Test
  fun `should decode a supplied GICS industry description`() {
    stubBothPages()

    val snapshot = vanguardHoldingsService.fetchHoldings(PORT_ID)

    expect(snapshot.holdings.first { it.name == "Alphabet Inc" }.industry).toEqual(GicsIndustry.INTERACTIVE_MEDIA_AND_SERVICES)
  }

  @Test
  fun `should decode a holding that reports no ticker`() {
    stubBothPages()

    val snapshot = vanguardHoldingsService.fetchHoldings(PORT_ID)

    expect(snapshot.holdings.first { it.name == "Rest Of Fund Ltd" }.ticker).toEqual(null)
  }

  @Test
  fun `should decode a holding that reports no industry`() {
    stubBothPages()

    val snapshot = vanguardHoldingsService.fetchHoldings(PORT_ID)

    expect(snapshot.holdings.first { it.name == "Rest Of Fund Ltd" }.industry).toEqual(null)
  }

  @Test
  fun `should throw when Vanguard reports GraphQL errors with a successful status`() {
    stubPage(Scenario.STARTED, null, """{"data":null,"errors":[{"message":"Variable portIds is invalid"}]}""")

    expect {
      vanguardHoldingsService.fetchHoldings(PORT_ID)
    }.toThrow<IllegalStateException>().messageToContain("Variable portIds is invalid")
  }

  private fun stubBothPages() {
    stubPage(Scenario.STARTED, "second-page", pageBody(FIRST_PAGE_ITEM, """"page-2-key""""))
    stubPage("second-page", null, pageBody(SECOND_PAGE_ITEM, "null"))
  }

  private fun stubPage(
    currentState: String,
    nextState: String?,
    body: String,
  ) {
    val stub =
      post(urlPathEqualTo(GRAPHQL_PATH))
        .inScenario(SCENARIO)
        .whenScenarioStateIs(currentState)
        .willReturn(aResponse().withStatus(200).withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE).withBody(body))
    wireMockServer.stubFor(nextState?.let { stub.willSetStateTo(it) } ?: stub)
  }

  private fun pageBody(
    item: String,
    lastItemKey: String,
  ): String =
    """
    {
      "data": {
        "borHoldings": [
          {
            "holdings": {
              "items": [$item],
              "totalHoldings": 6743,
              "lastItemKey": $lastItemKey
            }
          }
        ]
      }
    }
    """.trimIndent()

  companion object {
    private const val GRAPHQL_PATH = "/gpx/graphql"
    private const val PORT_ID = "E161"
    private const val SCENARIO = "vanguard-holdings"

    private val FIRST_PAGE_ITEM =
      """
      {
        "issuerName": "Alphabet Inc",
        "ticker": "GOOGL",
        "marketValuePercentage": 3.02968,
        "gicsIndustryDescription": "Interactive Media & Services",
        "securityType": "EQ.STOCK",
        "effectiveDate": "2026-08-31"
      }
      """.trimIndent()

    private val SECOND_PAGE_ITEM =
      """
      {
        "issuerName": "Rest Of Fund Ltd",
        "ticker": null,
        "marketValuePercentage": 97.06459,
        "gicsIndustryDescription": null,
        "securityType": "EQ.STOCK",
        "effectiveDate": "2026-08-31"
      }
      """.trimIndent()
  }
}
