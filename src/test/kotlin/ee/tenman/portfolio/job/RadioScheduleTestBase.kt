package ee.tenman.portfolio.job

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.kuku.KukuScheduleClient
import ee.tenman.portfolio.repository.RadioAiringRepository
import ee.tenman.portfolio.repository.RadioProgrammeRepository
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.radio.Capture
import ee.tenman.portfolio.service.radio.RadioProperties
import ee.tenman.portfolio.service.radio.RadioScheduleService
import io.mockk.mockk
import jakarta.annotation.Resource
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.wiremock.spring.InjectWireMock
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put as mvcPut

abstract class RadioScheduleTestBase {
  @Resource
  private lateinit var kukuScheduleClient: KukuScheduleClient

  @Resource
  protected lateinit var radioScheduleService: RadioScheduleService

  @Resource
  protected lateinit var radioProgrammeRepository: RadioProgrammeRepository

  @Resource
  protected lateinit var radioAiringRepository: RadioAiringRepository

  @Resource
  private lateinit var jobExecutionService: JobExecutionService

  @Resource
  protected lateinit var radioProperties: RadioProperties

  @Resource
  protected lateinit var clock: Clock

  @Resource
  protected lateinit var mockMvc: MockMvc

  @InjectWireMock
  private lateinit var wireMockServer: WireMockServer

  protected val tallinn: ZoneId = ZoneId.of("Europe/Tallinn")

  protected val day: LocalDate by lazy { LocalDate.now(clock).plusDays(1) }

  @BeforeEach
  fun setup() {
    wireMockServer.resetAll()
    radioProperties.recordings.toFile().deleteRecursively()
    Files.createDirectories(radioProperties.recordings)
  }

  protected fun at(time: String) = day.atTime(LocalTime.parse(time)).atZone(tallinn)

  protected fun episode(
    id: Long,
    show: Long,
    name: String,
    start: String,
    end: String,
    original: Long? = null,
  ) = episode(id, show, name, at(start), at(end), original)

  protected fun episode(
    id: Long,
    show: Long,
    name: String,
    start: ZonedDateTime,
    end: ZonedDateTime,
    original: Long? = null,
  ) = """{"id":$id,"start":"${start.toOffsetDateTime()}","end":"${end.toOffsetDateTime()}","title":"$name $day",""" +
    """"original_id":$original,"show":{"id":$show,"name":"$name","slug":"${SLUGS.getValue(show)}",""" +
    """"description_short":"Saade, mida juhivad Ülle ja Õie"}}"""

  protected fun load(vararg episodes: String) {
    wireMockServer.stubFor(
      get(urlPathEqualTo("/api/schedule"))
        .withQueryParam("domain", equalTo("kuku.pleier.ee"))
        .willReturn(okJson("""[{"day":"$day","episodes":[${episodes.joinToString(",")}]}]""")),
    )
    RadioScheduleJob(kukuScheduleClient, radioScheduleService, jobExecutionService, mockk(), clock).execute()
  }

  protected fun recording(
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

  protected fun choose(
    id: Long,
    record: Boolean,
  ) = mvcPut("/api/radio/airings/$id").contentType(APPLICATION_JSON).content("""{"record":$record}""")

  protected fun beat(station: Station) {
    Files.writeString(radioProperties.recordings.resolve(station.heartbeat), """{"seen":0,"until":${clock.instant().epochSecond + 60}}""")
  }

  protected fun plan(station: Station = Station.KUKU): List<Capture> =
    JsonMapperFactory.instance.readValue(Files.readString(radioProperties.recordings.resolve(station.plan)))

  private companion object {
    val SLUGS = mapOf(175L to "keskpaevatund", 231L to "tarkade-klubi")
  }
}
