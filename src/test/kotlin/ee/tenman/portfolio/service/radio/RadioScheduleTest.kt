package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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
  fun `should record an airing from five minutes before it to five minutes after it`() {
    val found = captures(listOf(airing("Keskpäevatund", "keskpaevatund", "2026-10-24T12:00", "2026-10-24T13:00")), tallinn)
    val start = at("2026-10-24T11:55").epochSecond
    val expected = Capture("2026-10-24-1200-keskpaevatund", listOf("Keskpäevatund"), start, at("2026-10-24T13:05").epochSecond)
    expect(found).toContainExactly(expected)
  }

  @Test
  fun `should record back to back airings as one recording named after both`() {
    val airings =
      listOf(
        airing("Tarkade klubi", "tarkade-klubi", "2026-10-24T13:00", "2026-10-24T14:00"),
        airing("Keskpäevatund", "keskpaevatund", "2026-10-24T12:00", "2026-10-24T13:00"),
      )
    expect(captures(airings, tallinn).map { it.name to it.programmes })
      .toContainExactly("2026-10-24-1200-keskpaevatund-tarkade-klubi" to listOf("Keskpäevatund", "Tarkade klubi"))
  }

  @Test
  fun `should keep airings an hour apart in separate recordings`() {
    val airings =
      listOf(
        airing("Neeme Raud. Siin", "neeme-raud-siin", "2026-10-24T09:00", "2026-10-24T11:00"),
        airing("Keskpäevatund", "keskpaevatund", "2026-10-24T12:00", "2026-10-24T13:00"),
      )
    expect(captures(airings, tallinn)).toHaveSize(2)
  }

  @Test
  fun `should name a recording after the minute its first airing starts`() {
    val found = captures(listOf(airing("Naistejutud Kukus", "naistejutud-kukus", "2026-10-25T00:00", "2026-10-25T01:00")), tallinn)
    expect(found.single().name).toEqual("2026-10-25-0000-naistejutud-kukus")
  }

  @Test
  fun `should give two airings of one programme on the same day their own recordings`() {
    val airings =
      listOf(
        airing("Naistejutud Kukus", "naistejutud-kukus", "2026-10-24T00:00", "2026-10-24T01:00"),
        airing("Naistejutud Kukus", "naistejutud-kukus", "2026-10-24T17:00", "2026-10-24T18:00"),
      )
    expect(captures(airings, tallinn).map { it.name })
      .toContainExactly("2026-10-24-0000-naistejutud-kukus", "2026-10-24-1700-naistejutud-kukus")
  }

  @Test
  fun `should turn a slug into a safe directory name`() {
    val found = captures(listOf(airing("Õhtu ÜLE", "Õhtu_ÜLE--Ööl", "2026-10-21T19:00", "2026-10-21T20:00")), tallinn)
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
      at("2026-10-24T11:55").epochSecond,
      at("2026-10-24T13:05").epochSecond,
    )

  private val afterwards = at("2026-10-24T13:06")

  private fun beat(until: String) = Heartbeat(at(until).epochSecond.toDouble())

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
    expect(missed(listOf(ended), emptySet(), null, afterwards)).toContainExactly(MissedCapture(ended, MissedReason.NEVER_RAN))
  }

  @Test
  fun `should blame a recorder that is down for a missed capture`() {
    expect(missed(listOf(ended), emptySet(), beat("2026-10-24T11:00"), afterwards).map { it.reason }).toContainExactly(MissedReason.DOWN)
  }

  @Test
  fun `should blame a running recorder that saved nothing for a missed capture`() {
    val found = missed(listOf(ended), emptySet(), beat("2026-10-24T13:07"), afterwards)
    expect(found.map { it.reason }).toContainExactly(MissedReason.SAVED_NOTHING)
  }

  @Test
  fun `should not miss a capture that left a recording directory`() {
    expect(missed(listOf(ended), setOf(ended.name), null, afterwards)).toHaveSize(0)
  }

  @Test
  fun `should not miss a capture that has not ended`() {
    expect(missed(listOf(ended), emptySet(), null, at("2026-10-24T13:00"))).toHaveSize(0)
  }

  @Test
  fun `should forget a missed capture a day after it ended`() {
    expect(missed(listOf(ended), emptySet(), null, at("2026-10-25T13:06"))).toHaveSize(0)
  }
}
