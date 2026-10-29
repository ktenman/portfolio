package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.RadioAiring
import ee.tenman.portfolio.domain.RadioProgramme
import ee.tenman.portfolio.repository.RadioAiringRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RadioDayTest {
  private fun recording(
    from: Long,
    to: Long,
  ) = AiringRecording("2026-10-17-1200-keskpaevatund", "Keskpäevatund", 0, 4200, from, to)

  private fun airing(state: AiringState) = DayAiring(1, "Keskpäevatund", null, 0, 3600, state, false, false, null, null)

  private fun service(
    now: String,
    airings: List<RadioAiring> = emptyList(),
    vararg shows: RadioProgramme,
  ): RadioDayService {
    val clock = Clock.fixed(Instant.parse(now), TALLINN)
    val names = programmes(*shows)
    return RadioDayService(
      mockk<RadioAiringRepository> {
        every { findStarting(any(), any()) } returns airings
        every { findAllByKukuIdIn(any()) } returns emptyList()
      },
      names,
      mockk<RadioArchiveService> { every { recordings() } returns emptyList() },
      RadioScheduleService(names, mockk(), mockk(), RadioProperties(), clock),
      clock,
    )
  }

  private fun aired(
    id: Long,
    show: RadioProgramme,
    hour: Int,
    original: Long? = null,
  ): RadioAiring {
    val at = Instant.ofEpochSecond(tick(THURSDAY, hour))
    return RadioAiring(id, show.id, show.name, at, at.plusSeconds(3600), original)
  }

  private fun tick(
    date: LocalDate,
    hour: Int,
  ) = date.atTime(hour, 0).atZone(TALLINN).toEpochSecond()

  @Test
  fun `should offer the calendar week from Monday to Sunday`() {
    expect(week(LocalDate.of(2026, 10, 22))).toEqual((19..25).map { LocalDate.of(2026, 10, it) })
  }

  @Test
  fun `should keep a Sunday in the week that started on Monday`() {
    expect(week(LocalDate.of(2026, 10, 25)).first()).toEqual(LocalDate.of(2026, 10, 19))
  }

  @Test
  fun `should cover an airing by the recording that holds most of it`() {
    val takes = listOf(Take("a", "Ilmaparandaja", 0, 200, false, null), Take("b", "Keskpäevatund", 3400, 4000, false, null))
    expect(covered(100, 3700, takes)).toEqual(AiringRecording("b", "Keskpäevatund", 3400, 4000, 3400, 3700))
  }

  @Test
  fun `should not count a recording that touches an airing for under a minute`() {
    expect(covered(1000, 4600, listOf(Take("a", "Keskpäevatund", 0, 1030, false, null)))).toEqual(null)
  }

  @Test
  fun `should call an airing recorded when its recording misses under a minute of it`() {
    expect(state(0, 3600, 9000, recording(30, 3600), false, null)).toEqual(AiringState.RECORDED)
  }

  @Test
  fun `should call an airing partial when a recording holds only its end`() {
    expect(state(0, 3600, 9000, recording(3300, 3600), false, null)).toEqual(AiringState.PARTIAL)
  }

  @Test
  fun `should call an airing on air while it airs`() {
    expect(state(0, 3600, 1800, null, false, null)).toEqual(AiringState.ON_AIR)
  }

  @Test
  fun `should call a planned airing recording while it airs`() {
    expect(state(0, 3600, 1800, recording(0, 1800), true, null)).toEqual(AiringState.RECORDING)
  }

  @Test
  fun `should call a repeat archived when its original was recorded`() {
    expect(state(7200, 10800, 0, null, false, AiringState.RECORDED)).toEqual(AiringState.ARCHIVED)
  }

  @Test
  fun `should call an ended airing without a recording missed`() {
    expect(state(0, 3600, 3600, null, true, AiringState.PARTIAL)).toEqual(AiringState.MISSED)
  }

  @Test
  fun `should call a later airing in the plan planned`() {
    expect(state(7200, 10800, 0, null, true, null)).toEqual(AiringState.PLANNED)
  }

  @Test
  fun `should leave a later airing open to record`() {
    expect(state(7200, 10800, 0, null, false, AiringState.PARTIAL)).toEqual(AiringState.OPEN)
  }

  @Test
  fun `should drop the date and repeat marks Kuku puts in a title`() {
    expect(episode("Keskpäevatund 17-10-2026**", "Keskpäevatund")).toEqual(null)
  }

  @Test
  fun `should match the programme name in any case`() {
    expect(episode("Emalaev 18-10-2026", "EMALAEV")).toEqual(null)
  }

  @Test
  fun `should keep an episode title`() {
    expect(episode("474 · Robin Juhkental**", "Naistejutud Kukus")).toEqual("474 · Robin Juhkental")
  }

  @Test
  fun `should collapse each run of missed airings into one block`() {
    val states = listOf(AiringState.MISSED, AiringState.MISSED, AiringState.RECORDED, AiringState.MISSED, AiringState.OPEN)
    expect(blocks(states.map(::airing)).map { it.missed to it.airings.size })
      .toEqual(listOf(true to 2, false to 1, true to 1, false to 1))
  }

  @Test
  fun `should clip recordings to the day`() {
    val takes =
      listOf(
        Take("b", "Õhtutund", 920, 1120, false, null),
        Take("a", "Keskpäevatund", 70, 170, false, null),
        Take("c", "Öötund", 1220, 1320, false, null),
      )
    expect(spans(takes, 100, 1000))
      .toEqual(listOf(RecordedSpan("a", "Keskpäevatund", 100, 150, false), RecordedSpan("b", "Õhtutund", 900, 1000, false)))
  }

  @Test
  fun `should mark the span of a take still being recorded`() {
    val takes = listOf(Take("a", "Kuku", 70, 170, false, null), Take("b", "Kuku", 920, 1120, true, null))
    expect(spans(takes, 100, 1000)).toEqual(listOf(RecordedSpan("a", "Kuku", 100, 150, false), RecordedSpan("b", "Kuku", 900, 1000, true)))
  }

  @Test
  fun `should draw a capture over the air time of its slot`() {
    val take = Take("2026-10-26-1000-kuue-samba-taga", "Kuue samba taga", 3610, 5430, false, Slot(3600, 5400))
    expect(spans(listOf(take), 0, 86400)).toEqual(listOf(RecordedSpan(take.name, "Kuue samba taga", 3600, 5400, false)))
  }

  @Test
  fun `should leave out a capture that stopped before the stream reached its slot`() {
    val take = Take("2026-10-26-1000-kuue-samba-taga", "Kuue samba taga", 3610, 3615, false, Slot(3600, 5400))
    expect(spans(listOf(take), 0, 86400)).toEqual(emptyList())
  }

  @Test
  fun `should plan only the coming originals of programmes recorded every time`() {
    val sihik = RadioProgramme(500, "Sihik", "sihik", recordEvery = true).apply { id = 1 }
    val õun = RadioProgramme(501, "Kukkuv Õun", "kukkuv-oun").apply { id = 2 }
    val noon = aired(2, sihik, 12)
    val past = aired(1, sihik, 6)
    val unticked = aired(3, õun, 15)
    val repeat = aired(4, sihik, 22, 2)
    val day = service("2026-10-29T08:00:00Z", listOf(past, noon, unticked, repeat), sihik, õun).day(THURSDAY)
    expect(day.planned).toEqual(listOf(PlannedSpan("Sihik", noon.startsAt.epochSecond, noon.endsAt.epochSecond)))
  }

  @Test
  fun `should clip a planned airing to the end of the broadcast day`() {
    expect(planned(listOf(airing(AiringState.PLANNED)), 1800)).toEqual(listOf(PlannedSpan("Keskpäevatund", 0, 1800)))
  }

  @Test
  fun `should tick six, noon, eighteen and midnight of a broadcast day`() {
    val date = LocalDate.of(2026, 10, 17)
    expect(ticks(date, TALLINN)).toEqual(
      listOf(
        DayTick(tick(date, 6), false),
        DayTick(tick(date, 12), false),
        DayTick(tick(date, 18), false),
        DayTick(ZonedDateTime.of(2026, 10, 18, 0, 0, 0, 0, TALLINN).toEpochSecond(), true),
      ),
    )
  }

  @Test
  fun `should open on the broadcast day still on air after midnight`() {
    expect(service("2026-10-27T00:30:00Z").day(null).date).toEqual(LocalDate.of(2026, 10, 26))
  }

  @Test
  fun `should run the broadcast day across the clock change for 25 hours`() {
    val day = service("2026-10-24T12:00:00Z").day(LocalDate.of(2026, 10, 24))
    expect(day.until - day.from).toEqual(90000)
  }

  companion object {
    private val TALLINN = ZoneId.of("Europe/Tallinn")
    private val THURSDAY = LocalDate.of(2026, 10, 29)
  }
}
