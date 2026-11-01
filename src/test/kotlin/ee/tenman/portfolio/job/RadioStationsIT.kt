package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.service.radio.AiringChoice
import ee.tenman.portfolio.service.radio.MissedReason
import ee.tenman.portfolio.service.radio.ProgrammeChoice
import ee.tenman.portfolio.service.radio.RecorderState
import ee.tenman.portfolio.service.radio.ScheduledAiring
import ee.tenman.portfolio.service.radio.ScheduledShow
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.time.ZonedDateTime
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet

@IntegrationTest
class RadioStationsIT : RadioScheduleTestBase() {
  private fun aripaev(
    id: Long,
    start: String,
    end: String,
  ) = aripaev(id, at(start), at(end))

  private fun aripaev(
    id: Long,
    start: ZonedDateTime,
    end: ZonedDateTime,
  ) = ScheduledAiring(
    id,
    ScheduledShow(ARIPAEV_SHOW, "Kuum tool", "kuum-tool", "Päeva põletavad küsimused"),
    "Kuum tool",
    start.toInstant(),
    end.toInstant(),
    null,
  )

  private fun replaceAripaev(vararg airings: ScheduledAiring) {
    radioScheduleService.replace(Station.ARIPAEV, airings.toList(), at("00:00").toInstant(), at("00:00").plusDays(1).toInstant())
  }

  private fun aripaevRecording(
    state: String,
    programme: String = "Kuum tool",
  ) {
    val directory = Files.createDirectories(radioProperties.recordings.resolve("2026-11-09-1100-aripaev-kuum-tool"))
    Files.writeString(
      directory.resolve("status.json"),
      """{"state":"$state","startedAt":${at("08:00").toEpochSecond()},"seconds":600,"programmes":["$programme"],"station":"ARIPAEV"}""",
    )
  }

  @Test
  fun `should count the recordings of shows of one name on two stations apart`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    val namesake = ScheduledShow(ARIPAEV_SHOW, "Keskpäevatund", "keskpaevatund", null)
    replaceAripaev(aripaev(ARIPAEV_AIRING, "13:00", "14:00").copy(show = namesake))
    recording("2026-11-08-1200-keskpaevatund", at("10:00"), 600)
    recording("2026-11-09-1200-keskpaevatund", at("10:00"), 600)
    aripaevRecording("captured", "Keskpäevatund")
    expect(radioScheduleService.programmes().associate { it.station to it.recordings })
      .toEqual(mapOf(Station.KUKU to 2, Station.ARIPAEV to 1))
  }

  @Test
  fun `should keep the airings of Kuku when the schedule of Äripäev is replaced`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    replaceAripaev(aripaev(ARIPAEV_AIRING, "12:00", "13:00"))
    expect(radioAiringRepository.findByExternalId(1)).notToEqualNull()
  }

  @Test
  fun `should keep the airings of Äripäev when the schedule of Kuku is imported`() {
    replaceAripaev(aripaev(ARIPAEV_AIRING, "12:00", "13:00"))
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    expect(radioAiringRepository.findByExternalId(ARIPAEV_AIRING)).notToEqualNull()
  }

  @Test
  fun `should drop an airing of Äripäev that left its schedule`() {
    replaceAripaev(aripaev(ARIPAEV_AIRING, "12:00", "13:00"), aripaev(ARIPAEV_AIRING + 1, "14:00", "15:00"))
    replaceAripaev(aripaev(ARIPAEV_AIRING, "12:00", "13:00"))
    expect(radioAiringRepository.findByExternalId(ARIPAEV_AIRING + 1)).toEqual(null)
  }

  @Test
  fun `should store a new airing and its programme under the station of the import`() {
    replaceAripaev(aripaev(ARIPAEV_AIRING, "12:00", "13:00"))
    val airing = radioAiringRepository.findByExternalId(ARIPAEV_AIRING)
    val programme = radioProgrammeRepository.findByExternalId(ARIPAEV_SHOW)
    expect(airing?.station to programme?.station).toEqual(Station.ARIPAEV to Station.ARIPAEV)
  }

  @Test
  fun `should store the rows of a Kuku import as Kuku`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    val airing = radioAiringRepository.findByExternalId(1)
    val programme = radioProgrammeRepository.findByExternalId(175)
    expect(airing?.station to programme?.station).toEqual(Station.KUKU to Station.KUKU)
  }

  @Test
  fun `should write an empty Äripäev plan beside an unchanged Kuku plan when only Kuku is ticked`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    replaceAripaev(aripaev(ARIPAEV_AIRING, "14:00", "15:00"))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    val slot = """"slot":{"start":${at("12:00").toEpochSecond()},"end":${at("13:00").toEpochSecond()}}"""
    val times = """"start":${at("12:00:10").toEpochSecond()},"end":${at("13:00:30").toEpochSecond()}"""
    val kuku = Files.readString(radioProperties.recordings.resolve("plan.json")).filterNot(Char::isWhitespace)
    expect(plan(Station.ARIPAEV).size to kuku)
      .toEqual(0 to """[{"name":"$day-1200-keskpaevatund","programmes":["Keskpäevatund"],$times,$slot}]""")
  }

  @Test
  fun `should give two stations at the same minute separate captures`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    replaceAripaev(aripaev(ARIPAEV_AIRING, "12:00", "13:00"))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    radioScheduleService.choose(ARIPAEV_SHOW, ProgrammeChoice(true))
    expect(plan().map { it.name } to plan(Station.ARIPAEV).map { it.name })
      .toEqual(listOf("$day-1200-keskpaevatund") to listOf("$day-1200-aripaev-kuum-tool"))
  }

  @Test
  fun `should write the plan of Äripäev into its own file`() {
    replaceAripaev(aripaev(ARIPAEV_AIRING, "12:00", "13:00"))
    radioScheduleService.record(ARIPAEV_AIRING, AiringChoice(true))
    expect(Files.exists(radioProperties.recordings.resolve("plan-aripaev.json"))).toEqual(true)
  }

  @Test
  fun `should plan only the airings of the given station`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    replaceAripaev(aripaev(ARIPAEV_AIRING, "12:05", "13:00"))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    radioScheduleService.choose(ARIPAEV_SHOW, ProgrammeChoice(true))
    val airings = radioAiringRepository.findByEndsAtAfterOrderByStartsAt(clock.instant())
    expect(radioScheduleService.planned(airings, Station.ARIPAEV).map { it.programmes })
      .toContainExactly(listOf("Kuum tool"))
  }

  @Test
  fun `should count the hours of Äripäev at its own bitrate`() {
    val station = radioScheduleService.station(Station.ARIPAEV)
    expect(station.hours).toEqual(station.freeBytes?.div(172_800_000))
  }

  @Test
  fun `should name the next airing of the given station`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    replaceAripaev(aripaev(ARIPAEV_AIRING, "14:00", "15:00"))
    expect(radioScheduleService.station(Station.ARIPAEV).next?.name).toEqual("Kuum tool")
  }

  @Test
  fun `should keep the next airing of Kuku when Äripäev airs sooner`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    replaceAripaev(aripaev(ARIPAEV_AIRING, "09:00", "10:00"))
    expect(radioScheduleService.station().next?.name).toEqual("Keskpäevatund")
  }

  @Test
  fun `should name what the given station has on air`() {
    val now = clock.instant().atZone(tallinn)
    load(episode(4, 175, "Keskpäevatund", now.minusHours(1), now.plusHours(1)))
    replaceAripaev(aripaev(ARIPAEV_AIRING, now.minusMinutes(5), now.plusMinutes(5)))
    expect(radioScheduleService.station(Station.ARIPAEV).onAir?.name).toEqual("Kuum tool")
  }

  @Test
  fun `should leave the capture of Kuku out of the station Äripäev`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    expect(radioScheduleService.station(Station.ARIPAEV).capture).toEqual(null)
  }

  @Test
  fun `should name the last recording of the given station`() {
    aripaevRecording("captured")
    recording("2026-11-09-1200-keskpaevatund", at("11:55"), 4200)
    expect(radioScheduleService.station(Station.ARIPAEV).last?.programme).toEqual("Kuum tool")
  }

  @Test
  fun `should keep the recordings of Äripäev out of the last recording of Kuku`() {
    aripaevRecording("captured")
    expect(radioScheduleService.station().last).toEqual(null)
  }

  @Test
  fun `should read the heartbeat of the given station`() {
    beat(Station.ARIPAEV)
    expect(radioScheduleService.station(Station.ARIPAEV).recorder to radioScheduleService.station().recorder)
      .toEqual(RecorderState.WAITING to RecorderState.OFF)
  }

  @Test
  fun `should not call Kuku recording while only Äripäev records`() {
    beat(Station.KUKU)
    beat(Station.ARIPAEV)
    aripaevRecording("recording")
    expect(radioScheduleService.station().recorder to radioScheduleService.station(Station.ARIPAEV).recorder)
      .toEqual(RecorderState.WAITING to RecorderState.RECORDING)
  }

  @Test
  fun `should judge a missed capture by the heartbeat of its own station`() {
    val now = clock.instant().atZone(tallinn)
    load(episode(4, 175, "Keskpäevatund", now.minusHours(2), now.minusHours(1)))
    replaceAripaev(aripaev(ARIPAEV_AIRING, now.minusHours(2), now.minusHours(1)))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    radioScheduleService.choose(ARIPAEV_SHOW, ProgrammeChoice(true))
    beat(Station.ARIPAEV)
    expect(radioScheduleService.missed().map { it.capture.programmes to it.reason }).toContainExactly(
      listOf("Keskpäevatund") to MissedReason.NEVER_RAN,
      listOf("Kuum tool") to MissedReason.SAVED_NOTHING,
    )
  }

  @Test
  fun `should report nothing missed by an idle Äripäev`() {
    val now = clock.instant().atZone(tallinn)
    load(episode(4, 175, "Keskpäevatund", now.minusHours(2), now.minusHours(1)))
    replaceAripaev(aripaev(ARIPAEV_AIRING, now.minusHours(2), now.minusHours(1)))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    expect(radioScheduleService.missed().map { it.capture.programmes }).toContainExactly(listOf("Keskpäevatund"))
  }

  @Test
  fun `should serve an Äripäev airing chosen once as planned`() {
    replaceAripaev(aripaev(ARIPAEV_AIRING, "13:00", "14:00"))
    mockMvc.perform(choose(ARIPAEV_AIRING, true)).andExpect(status().isNoContent)
    mockMvc
      .perform(mvcGet("/api/radio/schedule").param("date", "$day"))
      .andExpect(jsonPath("$.day[0].airings[0].state").value("PLANNED"))
  }

  companion object {
    private const val ARIPAEV_SHOW = (1L shl 48) + 1
    private const val ARIPAEV_AIRING = (1L shl 48) + 2
  }
}
