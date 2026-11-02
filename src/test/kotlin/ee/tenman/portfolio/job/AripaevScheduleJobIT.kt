package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.aripaev.AripaevScheduleClient
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.service.radio.ProgrammeChoice
import io.mockk.mockk
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test

@IntegrationTest
class AripaevScheduleJobIT : RadioScheduleTestBase() {
  @Resource
  private lateinit var aripaevScheduleClient: AripaevScheduleClient

  private fun respond(body: String) {
    wireMockServer.stubFor(
      get(urlPathEqualTo("/raadio/saatekava"))
        .withHeader("RSC", equalTo("1"))
        .willReturn(aResponse().withHeader("Content-Type", "text/x-component").withBody(body.toByteArray())),
    )
  }

  private fun schedule() =
    """30:[{"schedule":[""" +
      """{"start":"${at("11:00").toOffsetDateTime()}","end":"${at("12:00").toOffsetDateTime()}","series":"Muusika"},""" +
      """{"start":"${at("12:00").toOffsetDateTime()}","end":"${at("13:00").toOffsetDateTime()}","series":"Kuum tool",""" +
      """"seriesSlug":"kuum-tool","title":"Õie ülevaade","description":"${'$'}6b"}""" +
      """],"episodeMedia":{}}]"""

  private fun job() = AripaevScheduleJob(aripaevScheduleClient, radioScheduleService, jobExecutionService, mockk(), clock)

  @Test
  fun `should read Estonian letters from a response that names no charset`() {
    respond(schedule())
    expect(aripaevScheduleClient.getSchedule()).toContain("Õie ülevaade")
  }

  @Test
  fun `should import the shows under Äripäev`() {
    respond(schedule())
    job().execute()
    expect(radioScheduleService.programmes().map { it.name to it.station }).toContainExactly("Kuum tool" to Station.ARIPAEV)
  }

  @Test
  fun `should plan a chosen programme in the plan of Äripäev`() {
    respond(schedule())
    job().execute()
    radioScheduleService.choose(radioScheduleService.programmes().single().id, ProgrammeChoice(true))
    job().execute()
    expect(plan(Station.ARIPAEV).single().programmes).toEqual(listOf("Kuum tool"))
  }

  @Test
  fun `should keep the stored airings planned when a payload holds no schedule`() {
    respond(schedule())
    job().execute()
    radioScheduleService.choose(radioScheduleService.programmes().single().id, ProgrammeChoice(true))
    respond("""30:[{"children":"Saatekava"}]""")
    expect { job().execute() }.toThrow<IllegalStateException>()
    radioScheduleService.plan()
    expect(plan(Station.ARIPAEV).single().programmes).toEqual(listOf("Kuum tool"))
  }
}
