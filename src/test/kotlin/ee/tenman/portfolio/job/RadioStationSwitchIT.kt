package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.aripaev.AripaevScheduleClient
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.service.radio.ProgrammeChoice
import io.mockk.mockk
import jakarta.annotation.Resource
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import java.nio.file.Files
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet

@IntegrationTest
@TestPropertySource(properties = ["radio.stations=KUKU"])
class RadioStationSwitchIT : RadioScheduleTestBase() {
  @Resource
  private lateinit var aripaevScheduleClient: AripaevScheduleClient

  private fun tickAripaev() {
    replaceAripaev(aripaev(ARIPAEV_AIRING, "11:00", "12:00"))
    radioScheduleService.choose(ARIPAEV_SHOW, ProgrammeChoice(true))
  }

  @Test
  fun `should list Kuku alone among the stations when Äripäev is off`() {
    expect(radioScheduleService.stations().map { it.station }).toContainExactly(Station.KUKU)
  }

  @Test
  fun `should leave the plan of Äripäev untouched when Äripäev is off`() {
    val plan = radioProperties.recordings.resolve(Station.ARIPAEV.plan)
    Files.writeString(plan, "Ülle jättis selle siia")
    tickAripaev()
    radioScheduleService.plan()
    expect(Files.readString(plan)).toEqual("Ülle jättis selle siia")
  }

  @Test
  fun `should not report a ticked Äripäev airing missed when Äripäev is off`() {
    tickAripaev()
    expect(radioScheduleService.missed().keys).toContainExactly(Station.KUKU)
  }

  @Test
  fun `should leave the programmes of Äripäev out when Äripäev is off`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    tickAripaev()
    expect(radioScheduleService.programmes().map { it.station }).toContainExactly(Station.KUKU)
  }

  @Test
  fun `should keep listing the recordings of Äripäev when Äripäev is off`() {
    aripaevRecording("captured")
    mockMvc
      .perform(mvcGet("/api/radio"))
      .andExpect(jsonPath("$[0].name").value("2026-11-09-1100-aripaev-kuum-tool"))
  }

  @Test
  fun `should skip the Äripäev import when Äripäev is off`() {
    AripaevScheduleJob(aripaevScheduleClient, radioScheduleService, jobExecutionService, mockk(), clock, radioProperties).execute()
    expect(wireMockServer.findAll(getRequestedFor(urlPathEqualTo("/raadio/saatekava")))).toEqual(emptyList())
  }
}
