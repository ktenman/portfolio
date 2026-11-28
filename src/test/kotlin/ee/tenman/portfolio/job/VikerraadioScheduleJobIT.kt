package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.service.radio.ProgrammeChoice
import ee.tenman.portfolio.service.radio.id
import io.mockk.mockk
import org.junit.jupiter.api.Test

@IntegrationTest
class VikerraadioScheduleJobIT : ErrScheduleTestBase(Station.VIKERRAADIO) {
  @Test
  fun `should name a ticked capture of Vikerraadio after the station in its own plan`() {
    schedule(day, item(1282, "Päevakaja", "Päevakaja", at("18:00"), at("18:45")))
    job().execute()
    radioScheduleService.choose(id("VIKERRAADIO/1282"), ProgrammeChoice(true))
    expect(plan(Station.VIKERRAADIO).single().name).toEqual("$day-1800-vikerraadio-paevakaja")
  }

  private fun job() = VikerraadioScheduleJob(errScheduleClient, radioScheduleService, jobExecutionService, mockk(), clock, radioProperties)
}
