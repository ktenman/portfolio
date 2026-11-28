package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.service.radio.ProgrammeChoice
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.id
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@IntegrationTest
class Raadio2ScheduleJobIT : ErrScheduleTestBase(Station.RAADIO2) {
  @Test
  fun `should ask for eight days each from its midnight in Tallinn across the clock change`() {
    val date = LocalDate.of(2027, 3, 25)
    schedule(date, item(47, "R2 Kevad", "Kevade algus", date.atTime(13, 5).atZone(tallinn), date.atTime(14, 5).atZone(tallinn)))
    job(Clock.fixed(Instant.parse("2027-03-25T10:00:00Z"), tallinn)).execute()
    expect(requested().sorted())
      .toEqual(listOf(1805925600L, 1806012000L, 1806098400L, 1806184800L, 1806267600L, 1806354000L, 1806440400L, 1806526800L))
  }

  @Test
  fun `should keep the airings of Kuku through an import of Raadio 2`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    schedule(day, item(48, "R2 Õhtu", "Õhtune jutt ja muusika", at("18:00"), at("19:00")))
    job().execute()
    expect(radioAiringRepository.findAll().map { it.title }).toContain("Keskpäevatund $day", "Õhtune jutt ja muusika")
  }

  @Test
  fun `should link a repeat imported from ERR to its original`() {
    val original = item(46, "R2 Päev", "Päeva esimene tund", at("13:05"), at("14:05"))
    val repeat = item(46, "R2 Päev", "Päeva esimene tund, kordus", at("20:05"), at("21:05"), at("13:05"))
    schedule(day, original, repeat)
    job().execute()
    val airings = radioAiringRepository.findAll().associateBy { it.title }
    expect(airings.getValue("Päeva esimene tund, kordus").originalId).toEqual(airings.getValue("Päeva esimene tund").externalId)
  }

  @Test
  fun `should name a ticked capture of Raadio 2 after the station in its own plan`() {
    schedule(day, item(45, "R2 Top 15", "R2 Top 15", at("13:05"), at("14:05")))
    job().execute()
    radioScheduleService.choose(id("RAADIO2/45"), ProgrammeChoice(true))
    expect(plan(Station.RAADIO2).single().name).toEqual("$day-1305-raadio2-r2-top-15")
  }

  @Test
  fun `should title an airing after its series when ERR lists it without a heading or repeat fields`() {
    val times = """"startTime":${at("23:05").toEpochSecond()},"endTime":${at("23:55").toEpochSecond()}"""
    schedule(day, """{"radiomanId":49,"seriesName":"R2 Öö","programName":"R2 Öö",$times}""")
    job().execute()
    expect(radioAiringRepository.findAll().map { it.title }).toContain("R2 Öö")
  }

  @Test
  fun `should ask ERR for nothing when Raadio 2 is off`() {
    schedule(day, item(45, "R2 Top 15", "R2 Top 15", at("13:05"), at("14:05")))
    job(properties = RadioProperties(recordings = radioProperties.recordings, stations = listOf(Station.KUKU))).execute()
    expect(requested()).toBeEmpty()
  }

  private fun job(
    clock: Clock = this.clock,
    properties: RadioProperties = radioProperties,
  ) = Raadio2ScheduleJob(errScheduleClient, radioScheduleService, jobExecutionService, mockk(), clock, properties)
}
