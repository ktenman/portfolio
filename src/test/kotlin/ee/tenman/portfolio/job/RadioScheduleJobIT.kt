package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.kuku.KukuScheduleClient
import ee.tenman.portfolio.repository.RadioAiringRepository
import ee.tenman.portfolio.repository.RadioProgrammeRepository
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.AirSlot
import ee.tenman.portfolio.service.radio.AiringChoice
import ee.tenman.portfolio.service.radio.Capture
import ee.tenman.portfolio.service.radio.MissedReason
import ee.tenman.portfolio.service.radio.ProgrammeChoice
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RadioScheduleService
import ee.tenman.portfolio.service.radio.RecorderState
import ee.tenman.portfolio.service.radio.ScheduledAiring
import ee.tenman.portfolio.service.radio.ScheduledShow
import ee.tenman.portfolio.service.radio.Slot
import io.mockk.mockk
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.wiremock.spring.InjectWireMock
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put as mvcPut

@IntegrationTest
class RadioScheduleJobIT {
  @Resource
  private lateinit var kukuScheduleClient: KukuScheduleClient

  @Resource
  private lateinit var radioScheduleService: RadioScheduleService

  @Resource
  private lateinit var radioProgrammeRepository: RadioProgrammeRepository

  @Resource
  private lateinit var radioAiringRepository: RadioAiringRepository

  @Resource
  private lateinit var jobExecutionService: JobExecutionService

  @Resource
  private lateinit var radioProperties: RadioProperties

  @Resource
  private lateinit var clock: Clock

  @Resource
  private lateinit var mockMvc: MockMvc

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  private val tallinn = ZoneId.of("Europe/Tallinn")

  private val day by lazy { LocalDate.now(clock).plusDays(1) }

  @BeforeEach
  fun setup() {
    wireMockServer.resetAll()
    radioProperties.recordings.toFile().deleteRecursively()
    Files.createDirectories(radioProperties.recordings)
  }

  private fun at(time: String) = day.atTime(LocalTime.parse(time)).atZone(tallinn)

  private fun episode(
    id: Long,
    show: Long,
    name: String,
    start: String,
    end: String,
    original: Long? = null,
  ) = episode(id, show, name, at(start), at(end), original)

  private fun episode(
    id: Long,
    show: Long,
    name: String,
    start: ZonedDateTime,
    end: ZonedDateTime,
    original: Long? = null,
  ) = """{"id":$id,"start":"${start.toOffsetDateTime()}","end":"${end.toOffsetDateTime()}","title":"$name $day",""" +
    """"original_id":$original,"show":{"id":$show,"name":"$name","slug":"${SLUGS.getValue(show)}",""" +
    """"description_short":"Saade, mida juhivad Ülle ja Õie"}}"""

  private fun load(vararg episodes: String) {
    wireMockServer.stubFor(
      get(urlPathEqualTo("/api/schedule"))
        .withQueryParam("domain", equalTo("kuku.pleier.ee"))
        .willReturn(okJson("""[{"day":"$day","episodes":[${episodes.joinToString(",")}]}]""")),
    )
    RadioScheduleJob(kukuScheduleClient, radioScheduleService, jobExecutionService, mockk(), clock).execute()
  }

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

  private fun recording(
    name: String,
    start: ZonedDateTime,
    seconds: Long,
  ) {
    val directory = Files.createDirectories(radioProperties.recordings.resolve(name))
    Files.writeString(
      directory.resolve("status.json"),
      """{"state":"captured","startedAt":${start.toEpochSecond()},"seconds":$seconds,"programmes":["Keskpäevatund"]}""",
    )
  }

  private fun choose(
    id: Long,
    record: Boolean,
  ) = mvcPut("/api/radio/airings/$id").contentType(APPLICATION_JSON).content("""{"record":$record}""")

  private fun aripaevRecording(state: String) {
    val directory = Files.createDirectories(radioProperties.recordings.resolve("2026-11-09-1100-aripaev-kuum-tool"))
    Files.writeString(
      directory.resolve("status.json"),
      """{"state":"$state","startedAt":${at("08:00").toEpochSecond()},"seconds":600,"programmes":["Kuum tool"],"station":"ARIPAEV"}""",
    )
  }

  private fun beat(station: Station) {
    Files.writeString(radioProperties.recordings.resolve(station.heartbeat), """{"seen":0,"until":${clock.instant().epochSecond + 60}}""")
  }

  private fun plan(station: Station = Station.KUKU): List<Capture> =
    JsonMapperFactory.instance.readValue(Files.readString(radioProperties.recordings.resolve(station.plan)))

  @Test
  fun `should list a programme with the air time of its original airings only`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"), episode(2, 175, "Keskpäevatund", "18:00", "19:00", original = 1))
    expect(radioScheduleService.programmes().single().slots).toEqual(
      listOf(AirSlot(listOf(day.dayOfWeek), LocalTime.NOON, LocalTime.of(13, 0))),
    )
  }

  @Test
  fun `should keep the every time choice when the schedule is imported again`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    expect(radioProgrammeRepository.findByExternalId(175)?.recordEvery).toEqual(true)
  }

  @Test
  fun `should drop an airing that left the schedule`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"), episode(3, 231, "Tarkade klubi", "13:00", "14:00"))
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    expect(radioScheduleService.programmes().map { it.name }).toContainExactly("Keskpäevatund")
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
  fun `should plan to record a programme chosen every time shifted by the stream lag`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    expect(plan().single()).toEqual(
      Capture(
        "$day-1200-keskpaevatund",
        listOf("Keskpäevatund"),
        at("12:00:10").toEpochSecond(),
        at("13:00:30").toEpochSecond(),
        Slot(at("12:00").toEpochSecond(), at("13:00").toEpochSecond()),
      ),
    )
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
  fun `should find only the airings that overlap a recording begun after the first went on air`() {
    load(
      episode(1, 231, "Tarkade klubi", "11:00", "12:00"),
      episode(2, 175, "Keskpäevatund", "12:00", "13:00"),
      episode(3, 231, "Tarkade klubi", "13:30", "14:00"),
    )
    val found = radioAiringRepository.findOverlapping(at("12:00:10").toInstant(), at("13:00:30").toInstant())
    expect(found.map { it.externalId }).toContainExactly(2L)
  }

  @Test
  fun `should not plan to record a repeat`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"), episode(2, 175, "Keskpäevatund", "18:00", "19:00", original = 1))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    expect(plan()).toHaveSize(1)
  }

  @Test
  fun `should serve the air time as a time of day`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    mockMvc
      .perform(mvcGet("/api/radio/programmes"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$[0].slots[0].start").value("12:00:00"))
  }

  @Test
  fun `should answer not found for a programme missing from the schedule`() {
    mockMvc
      .perform(mvcPut("/api/radio/programmes/999").contentType(APPLICATION_JSON).content("""{"recordEvery":true}"""))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `should name the next airing and whether it will be recorded`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"), episode(3, 231, "Tarkade klubi", "13:00", "14:00"))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    val next = radioScheduleService.station().next
    expect(next?.name to next?.recorded).toEqual("Keskpäevatund" to true)
  }

  @Test
  fun `should name the next capture of the recorder`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    expect(radioScheduleService.station().capture?.start).toEqual(at("12:00:10").toEpochSecond())
  }

  @Test
  fun `should count the hours the free space holds of MP3 and its PCM`() {
    val station = radioScheduleService.station()
    expect(station.hours).toEqual(station.freeBytes?.div(230_400_000))
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
  fun `should give no free space when the recordings directory is missing`() {
    radioProperties.recordings.toFile().deleteRecursively()
    expect(radioScheduleService.station().freeBytes).toEqual(null)
  }

  @Test
  fun `should call the recorder off when the worker left no heartbeat`() {
    expect(radioScheduleService.station().recorder).toEqual(RecorderState.OFF)
  }

  @Test
  fun `should call the recorder waiting while the worker beats`() {
    beat(Station.KUKU)
    expect(radioScheduleService.station().recorder).toEqual(RecorderState.WAITING)
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
  fun `should report a chosen airing that ended without a recording`() {
    val now = clock.instant().atZone(tallinn)
    load(episode(4, 175, "Keskpäevatund", now.minusHours(2), now.minusHours(1)))
    radioScheduleService.choose(175, ProgrammeChoice(true))
    expect(radioScheduleService.missed().map { it.capture.programmes to it.reason })
      .toContainExactly(listOf("Keskpäevatund") to MissedReason.NEVER_RAN)
  }

  @Test
  fun `should plan to record an airing chosen once`() {
    load(episode(30, 231, "Tarkade klubi", "13:00", "14:00"))
    radioScheduleService.record(30, AiringChoice(true))
    expect(plan().map { it.name }).toContain("$day-1300-tarkade-klubi")
  }

  @Test
  fun `should plan to record a repeat chosen once`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"), episode(31, 175, "Keskpäevatund", "18:00", "19:00", original = 1))
    radioScheduleService.record(31, AiringChoice(true))
    expect(plan().map { it.name }).toContain("$day-1800-keskpaevatund")
  }

  @Test
  fun `should refuse to choose an airing that has begun`() {
    val now = clock.instant().atZone(tallinn)
    load(episode(32, 231, "Tarkade klubi", now.minusMinutes(10), now.plusMinutes(50)))
    mockMvc.perform(choose(32, true)).andExpect(status().isBadRequest)
  }

  @Test
  fun `should answer not found for an airing missing from the schedule`() {
    mockMvc.perform(choose(999, true)).andExpect(status().isNotFound)
  }

  @Test
  fun `should serve an airing chosen once as planned`() {
    load(episode(33, 231, "Tarkade klubi", "13:00", "14:00"))
    mockMvc.perform(choose(33, true)).andExpect(status().isNoContent)
    mockMvc
      .perform(mvcGet("/api/radio/schedule").param("date", "$day"))
      .andExpect(jsonPath("$.day[0].airings[0].state").value("PLANNED"))
      .andExpect(jsonPath("$.day[0].airings[0].once").value(true))
  }

  @Test
  fun `should serve an Äripäev airing chosen once as planned`() {
    replaceAripaev(aripaev(ARIPAEV_AIRING, "13:00", "14:00"))
    mockMvc.perform(choose(ARIPAEV_AIRING, true)).andExpect(status().isNoContent)
    mockMvc
      .perform(mvcGet("/api/radio/schedule").param("date", "$day"))
      .andExpect(jsonPath("$.day[0].airings[0].state").value("PLANNED"))
  }

  @Test
  fun `should serve an airing a recording holds as recorded`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    recording("$day-1200-keskpaevatund", at("11:55"), 4200)
    mockMvc
      .perform(mvcGet("/api/radio/schedule").param("date", "$day"))
      .andExpect(jsonPath("$.day[0].airings[0].state").value("RECORDED"))
      .andExpect(jsonPath("$.day[0].airings[0].recording.name").value("$day-1200-keskpaevatund"))
  }

  @Test
  fun `should serve the night after midnight apart from the day`() {
    val night = episode(34, 175, "Keskpäevatund", at("02:00").plusDays(1), at("03:00").plusDays(1), original = 1)
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"), night)
    mockMvc
      .perform(mvcGet("/api/radio/schedule").param("date", "$day"))
      .andExpect(jsonPath("$.night[0].airings[0].original.start").value(at("12:00").toEpochSecond()))
  }

  @Test
  fun `should keep the description of a programme`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    expect(radioScheduleService.programmes().single().description).notToEqualNull().toEqual("Saade, mida juhivad Ülle ja Õie")
  }

  companion object {
    private val SLUGS = mapOf(175L to "keskpaevatund", 231L to "tarkade-klubi")
    private const val ARIPAEV_SHOW = (1L shl 48) + 1
    private const val ARIPAEV_AIRING = (1L shl 48) + 2
  }
}
