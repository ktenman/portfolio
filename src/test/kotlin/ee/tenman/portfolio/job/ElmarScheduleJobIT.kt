package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.service.radio.ProgrammeChoice
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.nio.file.Files

@IntegrationTest
class ElmarScheduleJobIT : RadioScheduleTestBase() {
  private fun elmar(vararg episodes: String) {
    stub("elmar.pleier.ee", *episodes)
    ElmarScheduleJob(kukuScheduleClient, radioScheduleService, jobExecutionService, mockk(), clock, radioProperties).execute()
  }

  @Test
  fun `should store the airings of Elmar under Elmar`() {
    elmar(episode(ELMAR_AIRING, 231, "Tarkade klubi", "14:00", "15:00"))
    expect(radioAiringRepository.findByExternalId(ELMAR_AIRING)?.station).toEqual(Station.ELMAR)
  }

  @Test
  fun `should keep the airings of Kuku when Elmar is imported`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    elmar(episode(ELMAR_AIRING, 231, "Tarkade klubi", "14:00", "15:00"))
    expect(radioAiringRepository.findByExternalId(1)?.station).toEqual(Station.KUKU)
  }

  @Test
  fun `should name an Elmar capture with the elmar prefix`() {
    elmar(episode(ELMAR_AIRING, 231, "Tarkade klubi", "14:00", "15:00"))
    radioScheduleService.choose(231, ProgrammeChoice(true))
    expect(Files.readString(radioProperties.recordings.resolve(Station.ELMAR.plan))).toContain("-1400-elmar-tarkade-klubi")
  }

  companion object {
    private const val ELMAR_AIRING = 3_000_001L
  }
}
