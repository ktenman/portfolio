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
import ee.tenman.portfolio.kuku.KukuScheduleClient
import ee.tenman.portfolio.repository.RadioAiringRepository
import ee.tenman.portfolio.repository.RadioProgrammeRepository
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.AirSlot
import ee.tenman.portfolio.service.radio.AiringChoice
import ee.tenman.portfolio.service.radio.Capture
import ee.tenman.portfolio.service.radio.HEARTBEAT_FILE
import ee.tenman.portfolio.service.radio.MissedReason
import ee.tenman.portfolio.service.radio.PLAN_FILE
import ee.tenman.portfolio.service.radio.ProgrammeChoice
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RadioScheduleService
import ee.tenman.portfolio.service.radio.RecorderState
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

  private fun plan(): List<Capture> = JsonMapperFactory.instance.readValue(Files.readString(radioProperties.recordings.resolve(PLAN_FILE)))

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
    expect(radioProgrammeRepository.findByKukuId(175)?.recordEvery).toEqual(true)
  }

  @Test
  fun `should drop an airing that left the schedule`() {
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"), episode(3, 231, "Tarkade klubi", "13:00", "14:00"))
    load(episode(1, 175, "Keskpäevatund", "12:00", "13:00"))
    expect(radioScheduleService.programmes().map { it.name }).toContainExactly("Keskpäevatund")
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
  fun `should find only the airings that overlap a recording begun after the first went on air`() {
    load(
      episode(1, 231, "Tarkade klubi", "11:00", "12:00"),
      episode(2, 175, "Keskpäevatund", "12:00", "13:00"),
      episode(3, 231, "Tarkade klubi", "13:30", "14:00"),
    )
    val found = radioAiringRepository.findOverlapping(at("12:00:10").toInstant(), at("13:00:30").toInstant())
    expect(found.map { it.kukuId }).toContainExactly(2L)
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
    Files.writeString(radioProperties.recordings.resolve(HEARTBEAT_FILE), """{"seen":0,"until":${clock.instant().epochSecond + 60}}""")
    expect(radioScheduleService.station().recorder).toEqual(RecorderState.WAITING)
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
  }
}
