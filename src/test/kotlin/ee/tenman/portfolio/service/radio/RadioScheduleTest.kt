package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.RadioAiring
import ee.tenman.portfolio.domain.Station
import ee.tenman.portfolio.kuku.KukuEpisode
import ee.tenman.portfolio.kuku.KukuShow
import ee.tenman.portfolio.repository.RadioAiringRepository
import ee.tenman.portfolio.repository.RadioProgrammeRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RadioScheduleTest {
  private val tallinn = ZoneId.of("Europe/Tallinn")

  private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(tallinn).toInstant()

  private fun airing(
    programme: String,
    slug: String,
    start: String,
    end: String,
  ) = PlannedAiring(programme, slug, at(start), at(end))

  @Test
  fun `should shift a capture by the stream lag to hold fifteen seconds either side of the airing`() {
    val found = captures(listOf(airing("Keskpäevatund", "keskpaevatund", "2026-10-24T12:00", "2026-10-24T13:00")), Station.KUKU, tallinn)
    val slot = Slot(at("2026-10-24T12:00").epochSecond, at("2026-10-24T13:00").epochSecond)
    val expected = Capture("2026-10-24-1200-keskpaevatund", listOf("Keskpäevatund"), slot.start + 5, slot.end + 35, slot)
    expect(found).toContainExactly(expected)
  }

  @Test
  fun `should shift an Äripäev capture by that stream's longer lag`() {
    val found = captures(listOf(airing("Kuum tool", "kuum-tool", "2026-11-09T11:00", "2026-11-09T12:00")), Station.ARIPAEV, tallinn)
    val slot = Slot(at("2026-11-09T11:00").epochSecond, at("2026-11-09T12:00").epochSecond)
    val expected = Capture("2026-11-09-1100-aripaev-kuum-tool", listOf("Kuum tool"), slot.start + 15, slot.end + 45, slot)
    expect(found).toContainExactly(expected)
  }

  @Test
  fun `should give back to back airings of two programmes their own recordings`() {
    val airings =
      listOf(
        airing("Sihik", "sihik", "2026-11-02T12:00", "2026-11-02T13:00"),
        airing("Digitund", "digitund", "2026-11-02T11:00", "2026-11-02T12:00"),
      )
    expect(captures(airings, Station.KUKU, tallinn).map { it.name to it.programmes })
      .toContainExactly("2026-11-02-1100-digitund" to listOf("Digitund"), "2026-11-02-1200-sihik" to listOf("Sihik"))
  }

  @Test
  fun `should give airings ten minutes apart their own recordings`() {
    val airings =
      listOf(
        airing("Digitund", "digitund", "2026-10-26T11:00", "2026-10-26T11:50"),
        airing("Keskpäevatund", "keskpaevatund", "2026-10-26T12:00", "2026-10-26T13:00"),
      )
    expect(captures(airings, Station.KUKU, tallinn).map { it.name })
      .toContainExactly("2026-10-26-1100-digitund", "2026-10-26-1200-keskpaevatund")
  }

  @Test
  fun `should plan no recording for a zero length airing`() {
    val marker = airing("Sihik", "sihik", "2026-10-29T13:07", "2026-10-29T13:07")
    expect(captures(listOf(marker), Station.KUKU, tallinn)).toBeEmpty()
  }

  @Test
  fun `should name a recording after the minute its airing starts`() {
    val airings = listOf(airing("Naistejutud Kukus", "naistejutud-kukus", "2026-10-25T00:00", "2026-10-25T01:00"))
    val found = captures(airings, Station.KUKU, tallinn)
    expect(found.single().name).toEqual("2026-10-25-0000-naistejutud-kukus")
  }

  @Test
  fun `should give two airings of one programme on the same day their own recordings`() {
    val airings =
      listOf(
        airing("Naistejutud Kukus", "naistejutud-kukus", "2026-10-24T00:00", "2026-10-24T01:00"),
        airing("Naistejutud Kukus", "naistejutud-kukus", "2026-10-24T17:00", "2026-10-24T18:00"),
      )
    expect(captures(airings, Station.KUKU, tallinn).map { it.name })
      .toContainExactly("2026-10-24-0000-naistejutud-kukus", "2026-10-24-1700-naistejutud-kukus")
  }

  @Test
  fun `should name a capture of Äripäev after the minute and the station`() {
    val found = captures(listOf(airing("Kuum tool", "kuum-tool", "2026-11-09T11:00", "2026-11-09T12:00")), Station.ARIPAEV, tallinn)
    expect(found.single().name).toEqual("2026-11-09-1100-aripaev-kuum-tool")
  }

  @Test
  fun `should turn a slug into a safe directory name`() {
    val found = captures(listOf(airing("Õhtu ÜLE", "Õhtu_ÜLE--Ööl", "2026-10-21T19:00", "2026-10-21T20:00")), Station.KUKU, tallinn)
    expect(found.single().name).toEqual("2026-10-21-1900-htu-le-l")
  }

  @Test
  fun `should group the weekdays that share an air time`() {
    val times =
      listOf(
        at("2026-10-20T06:00") to at("2026-10-20T10:00"),
        at("2026-10-19T06:00") to at("2026-10-19T10:00"),
        at("2026-10-24T08:00") to at("2026-10-24T09:00"),
      )
    expect(slots(times, tallinn)).toContainExactly(
      AirSlot(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), LocalTime.of(6, 0), LocalTime.of(10, 0)),
      AirSlot(listOf(DayOfWeek.SATURDAY), LocalTime.of(8, 0), LocalTime.of(9, 0)),
    )
  }

  private val ended =
    Capture(
      "2026-10-24-1200-keskpaevatund",
      listOf("Keskpäevatund"),
      at("2026-10-24T12:00:10").epochSecond,
      at("2026-10-24T13:00:30").epochSecond,
      Slot(at("2026-10-24T12:00").epochSecond, at("2026-10-24T13:00").epochSecond),
    )

  private val afterwards = at("2026-10-24T13:06")

  private fun beat(until: String) = Heartbeat(at(until).epochSecond.toDouble())

  private fun take(
    start: Long,
    end: Long,
  ) = Take("2026-10-24-1200-keskpaevatund-päevakaja", "Keskpäevatund", start, end, false, null, Station.KUKU)

  @Test
  fun `should call the recorder off when it left no heartbeat`() {
    expect(recorder(null, false, afterwards)).toEqual(RecorderState.OFF)
  }

  @Test
  fun `should call the recorder off when its heartbeat ran out over two minutes ago`() {
    expect(recorder(beat("2026-10-24T13:03"), false, afterwards)).toEqual(RecorderState.OFF)
  }

  @Test
  fun `should call the recorder recording while a recording is under way`() {
    expect(recorder(beat("2026-10-24T13:05"), true, afterwards)).toEqual(RecorderState.RECORDING)
  }

  @Test
  fun `should call the recorder waiting between recordings`() {
    expect(recorder(beat("2026-10-24T13:07"), false, afterwards)).toEqual(RecorderState.WAITING)
  }

  @Test
  fun `should miss a capture that ended without a recording from a recorder that never ran`() {
    expect(missed(listOf(ended), emptySet(), emptyList(), null, afterwards)).toContainExactly(MissedCapture(ended, MissedReason.NEVER_RAN))
  }

  @Test
  fun `should blame a recorder that is down for a missed capture`() {
    val found = missed(listOf(ended), emptySet(), emptyList(), beat("2026-10-24T11:00"), afterwards)
    expect(found.map { it.reason }).toContainExactly(MissedReason.DOWN)
  }

  @Test
  fun `should blame a running recorder that saved nothing for a missed capture`() {
    val found = missed(listOf(ended), emptySet(), emptyList(), beat("2026-10-24T13:07"), afterwards)
    expect(found.map { it.reason }).toContainExactly(MissedReason.SAVED_NOTHING)
  }

  @Test
  fun `should not miss a capture that left a recording directory`() {
    expect(missed(listOf(ended), setOf(ended.name), emptyList(), null, afterwards)).toHaveSize(0)
  }

  @Test
  fun `should not miss a capture that a recording under another name covers`() {
    expect(missed(listOf(ended), emptySet(), listOf(take(ended.start, ended.end)), null, afterwards)).toHaveSize(0)
  }

  @Test
  fun `should still miss a capture when the recording before it overlaps by only seconds`() {
    val found = missed(listOf(ended), emptySet(), listOf(take(ended.start - 3600, ended.start + 20)), null, afterwards)
    expect(found.map { it.capture }).toContainExactly(ended)
  }

  @Test
  fun `should not miss a capture that has not ended`() {
    expect(missed(listOf(ended), emptySet(), emptyList(), null, at("2026-10-24T13:00"))).toHaveSize(0)
  }

  @Test
  fun `should forget a missed capture a day after it ended`() {
    expect(missed(listOf(ended), emptySet(), emptyList(), null, at("2026-10-25T13:06"))).toHaveSize(0)
  }

  @Test
  fun `should report nothing for an idle station with no heartbeat and no captures`() {
    expect(missed(emptyList(), emptySet(), emptyList(), null, afterwards)).toHaveSize(0)
  }

  @Test
  fun `should count a deleted recording as recorded`(
    @TempDir root: Path,
  ) {
    Files.createDirectory(root.resolve("2026-10-18-1155-keskpaevatund"))
    Files.writeString(root.resolve(DELETED_FILE), """["2026-10-19-1023-kuku"]""")
    expect(recorded(root)).toContain("2026-10-18-1155-keskpaevatund", "2026-10-19-1023-kuku")
  }

  @Test
  fun `should not report a deleted capture as missed`(
    @TempDir root: Path,
  ) {
    Files.writeString(root.resolve(DELETED_FILE), """["${ended.name}"]""")
    expect(missed(listOf(ended), recorded(root), emptyList(), null, afterwards)).toHaveSize(0)
  }

  @Test
  fun `should read a status file without a station as Kuku`(
    @TempDir directory: Path,
  ) {
    Files.writeString(directory.resolve(STATUS_FILE), """{"state":"captured","startedAt":1792230900.0}""")
    expect(readStatus(directory).station).toEqual(Station.KUKU)
  }

  @Test
  fun `should read the station a status file names`(
    @TempDir directory: Path,
  ) {
    Files.writeString(directory.resolve(STATUS_FILE), """{"state":"captured","station":"ARIPAEV"}""")
    expect(readStatus(directory).station).toEqual(Station.ARIPAEV)
  }

  @Test
  fun `should need the hours of storage the stations table lists`() {
    expect(Station.entries.map { it.bytesPerHour }).toContainExactly(230_400_000L, 172_800_000L, 230_400_000L, 259_200_000L)
  }

  @Test
  fun `should keep the plan and heartbeat of Kuku under the names the recorder reads`() {
    expect(Station.KUKU.plan to Station.KUKU.heartbeat).toEqual("plan.json" to "heartbeat.json")
  }

  @Test
  fun `should keep the plan and heartbeat of Äripäev under the names its recorder reads`() {
    expect(Station.ARIPAEV.plan to Station.ARIPAEV.heartbeat).toEqual("plan-aripaev.json" to "heartbeat-aripaev.json")
  }

  @Test
  fun `should map a Kuku row to a scheduled airing`() {
    val start = OffsetDateTime.parse("2026-11-02T12:00:00+02:00")
    val end = start.plusHours(1)
    val row = KukuEpisode(7, start, end, "Õhtu uudised", 5, KukuShow(175, "Keskpäevatund", "keskpaevatund", "Ülle saade"))
    val show = ScheduledShow(175, "Keskpäevatund", "keskpaevatund", "Ülle saade")
    expect(row.scheduled()).toEqual(ScheduledAiring(7, show, "Õhtu uudised", start.toInstant(), end.toInstant(), 5))
  }

  @Test
  fun `should leave out a Kuku row with no show`() {
    val start = OffsetDateTime.parse("2026-11-02T12:00:00+02:00")
    expect(KukuEpisode(7, start, start.plusHours(1), "Muusika").scheduled()).toEqual(null)
  }

  @Test
  fun `should name the station whose schedule lists no programmes`() {
    val service = RadioScheduleService(mockk(), mockk(), mockk(), RadioProperties(), Clock.systemUTC())
    expect { service.replace(Station.ARIPAEV, emptyList(), Instant.EPOCH, Instant.EPOCH.plusSeconds(60)) }
      .toThrow<IllegalStateException>()
      .messageToContain("Äripäev schedule from 1970-01-01T00:00:00Z to 1970-01-01T00:01:00Z lists no programmes")
  }

  @Test
  fun `should name the station of an airing that has begun`() {
    val repository = mockk<RadioAiringRepository>()
    val airing = RadioAiring(7, 1, "Kuum tool", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), station = Station.ARIPAEV)
    every { repository.findByExternalId(7) } returns airing
    val service = RadioScheduleService(mockk(), repository, mockk(), RadioProperties(), Clock.systemUTC())
    expect { service.record(7, AiringChoice(true)) }
      .toThrow<IllegalArgumentException>()
      .messageToContain("Äripäev airing 7 began at 1970-01-01T00:00:00Z and can no longer be chosen")
  }

  @Test
  fun `should write the plans of two imports that finish at once`(
    @TempDir directory: Path,
  ) {
    val airings = mockk<RadioAiringRepository>()
    every { airings.findByEndsAtAfterOrderByStartsAt(any()) } returns emptyList()
    val programmes = mockk<RadioProgrammeRepository>()
    every { programmes.findAllById(any<Iterable<Long>>()) } returns emptyList()
    val service = RadioScheduleService(programmes, airings, mockk(), RadioProperties(recordings = directory), Clock.systemUTC())
    val pool = Executors.newFixedThreadPool(2)
    val runs = pool.invokeAll(List(500) { Callable { runCatching { service.plan() } } }, 1, TimeUnit.MINUTES)
    pool.shutdownNow()
    expect(runs.map { it.get() }.filter { it.isFailure }).toHaveSize(0)
  }
}
