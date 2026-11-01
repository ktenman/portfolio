package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.RadioAiring
import ee.tenman.portfolio.domain.RadioProgramme
import ee.tenman.portfolio.domain.Station
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

  private fun airing(state: AiringState) = DayAiring(1, "Keskpäevatund", null, 0, 3600, state, false, false, null, null, Station.KUKU)

  private fun service(
    now: String,
    airings: List<RadioAiring> = emptyList(),
    vararg shows: RadioProgramme,
    recordings: List<RecordingSummary> = emptyList(),
  ): RadioDayService {
    val clock = Clock.fixed(Instant.parse(now), TALLINN)
    val names = programmes(*shows)
    return RadioDayService(
      mockk<RadioAiringRepository> {
        every { findStarting(any(), any()) } returns airings
        every { findAllByExternalIdIn(any()) } returns emptyList()
      },
      names,
      mockk<RadioArchiveService> { every { recordings() } returns recordings },
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
    return RadioAiring(id, show.id, show.name, at, at.plusSeconds(3600), original, station = show.station)
  }

  private fun held(
    airing: RadioAiring,
    station: Station,
  ) = RecordingSummary(
    "2026-10-29-1200-sihik",
    "Sihik",
    RecordingStatus(
      RecordingState.CAPTURED,
      airing.startsAt.epochSecond.toDouble(),
      3620.0,
      slot = Slot(airing.startsAt.epochSecond, airing.endsAt.epochSecond),
      station = station,
    ),
    0,
    false,
    null,
    null,
    null,
    null,
    null,
    emptyList(),
    emptyList(),
    emptyList(),
    null,
    emptyList(),
  )

  private fun rows(day: RadioDay) = (day.day + day.night).flatMap { it.airings }

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
    val takes =
      listOf(
        Take("a", "Ilmaparandaja", 0, 200, false, null, Station.KUKU),
        Take("b", "Keskpäevatund", 3400, 4000, false, null, Station.KUKU),
      )
    expect(covered(100, 3700, takes)).toEqual(AiringRecording("b", "Keskpäevatund", 3400, 4000, 3400, 3700))
  }

  @Test
  fun `should not count a recording that touches an airing for under a minute`() {
    expect(covered(1000, 4600, listOf(Take("a", "Keskpäevatund", 0, 1030, false, null, Station.KUKU)))).toEqual(null)
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
        Take("b", "Õhtutund", 920, 1120, false, null, Station.KUKU),
        Take("a", "Keskpäevatund", 70, 170, false, null, Station.KUKU),
        Take("c", "Öötund", 1220, 1320, false, null, Station.KUKU),
      )
    expect(spans(takes, 100, 1000))
      .toEqual(
        listOf(
          RecordedSpan("a", "Keskpäevatund", 100, 150, false, Station.KUKU),
          RecordedSpan("b", "Õhtutund", 900, 1000, false, Station.KUKU),
        ),
      )
  }

  @Test
  fun `should mark the span of a take still being recorded`() {
    val takes = listOf(Take("a", "Kuku", 70, 170, false, null, Station.KUKU), Take("b", "Kuku", 920, 1120, true, null, Station.KUKU))
    expect(spans(takes, 100, 1000)).toEqual(
      listOf(RecordedSpan("a", "Kuku", 100, 150, false, Station.KUKU), RecordedSpan("b", "Kuku", 900, 1000, true, Station.KUKU)),
    )
  }

  @Test
  fun `should draw a capture over the air time of its slot`() {
    val take = Take("2026-10-26-1000-kuue-samba-taga", "Kuue samba taga", 3610, 5430, false, Slot(3600, 5400), Station.KUKU)
    expect(spans(listOf(take), 0, 86400)).toEqual(listOf(RecordedSpan(take.name, "Kuue samba taga", 3600, 5400, false, Station.KUKU)))
  }

  @Test
  fun `should leave out a capture that stopped before the stream reached its slot`() {
    val take = Take("2026-10-26-1000-kuue-samba-taga", "Kuue samba taga", 3610, 3615, false, Slot(3600, 5400), Station.KUKU)
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
    expect(day.planned).toEqual(listOf(PlannedSpan("Sihik", noon.startsAt.epochSecond, noon.endsAt.epochSecond, Station.KUKU)))
  }

  @Test
  fun `should sum the time of the airings planned for the day`() {
    val sihik = RadioProgramme(500, "Sihik", "sihik", recordEvery = true).apply { id = 1 }
    val day = service("2026-10-29T08:00:00Z", listOf(aired(1, sihik, 12), aired(2, sihik, 15)), sihik).day(THURSDAY)
    expect(day.plannedSeconds).toEqual(7200)
  }

  @Test
  fun `should plan no time on a day that has passed`() {
    val sihik = RadioProgramme(500, "Sihik", "sihik", recordEvery = true).apply { id = 1 }
    val day = service("2026-10-30T08:00:00Z", listOf(aired(1, sihik, 12), aired(2, sihik, 15)), sihik).day(THURSDAY)
    expect(day.plannedSeconds).toEqual(0)
  }

  @Test
  fun `should not cover an Äripäev airing with a Kuku take at the same time`() {
    val tool = RadioProgramme(1_000_001, "Kuum tool", "kuum-tool", station = Station.ARIPAEV).apply { id = 2 }
    val airing = aired(1, tool, 12)
    val day = service("2026-10-29T16:00:00Z", listOf(airing), tool, recordings = listOf(held(airing, Station.KUKU))).day(THURSDAY)
    expect(rows(day).single().state).toEqual(AiringState.MISSED)
  }

  @Test
  fun `should cover an Äripäev airing with an Äripäev take`() {
    val tool = RadioProgramme(1_000_001, "Kuum tool", "kuum-tool", station = Station.ARIPAEV).apply { id = 2 }
    val airing = aired(1, tool, 12)
    val day = service("2026-10-29T16:00:00Z", listOf(airing), tool, recordings = listOf(held(airing, Station.ARIPAEV))).day(THURSDAY)
    expect(rows(day).single().state).toEqual(AiringState.RECORDED)
  }

  @Test
  fun `should hold no Kuku row in a day scoped to Äripäev`() {
    val sihik = RadioProgramme(500, "Sihik", "sihik").apply { id = 1 }
    val tool = RadioProgramme(1_000_001, "Kuum tool", "kuum-tool", station = Station.ARIPAEV).apply { id = 2 }
    val airings = listOf(aired(1, sihik, 12), aired(2, tool, 15))
    val day = service("2026-10-29T08:00:00Z", airings, sihik, tool).day(THURSDAY, Station.ARIPAEV)
    expect(rows(day).map { it.station }).toEqual(listOf(Station.ARIPAEV))
  }

  @Test
  fun `should sum only the spans of the station a day is scoped to`() {
    val tool = RadioProgramme(1_000_001, "Kuum tool", "kuum-tool", station = Station.ARIPAEV).apply { id = 2 }
    val sihik = RadioProgramme(500, "Sihik", "sihik").apply { id = 1 }
    val own = aired(2, tool, 12)
    val other = aired(1, sihik, 15)
    val recordings = listOf(held(own, Station.ARIPAEV), held(other, Station.KUKU))
    val day = service("2026-10-29T16:00:00Z", listOf(own, other), sihik, tool, recordings = recordings).day(THURSDAY, Station.ARIPAEV)
    expect(day.recorded.map { it.station } to day.seconds).toEqual(listOf(Station.ARIPAEV) to 3600L)
  }

  @Test
  fun `should sum the airtime of both stations on a day with every station`() {
    val tool = RadioProgramme(1_000_001, "Kuum tool", "kuum-tool", station = Station.ARIPAEV).apply { id = 2 }
    val sihik = RadioProgramme(500, "Sihik", "sihik").apply { id = 1 }
    val own = aired(2, tool, 12)
    val other = aired(1, sihik, 12)
    val recordings = listOf(held(own, Station.ARIPAEV), held(other, Station.KUKU))
    val day = service("2026-10-29T16:00:00Z", listOf(own, other), sihik, tool, recordings = recordings).day(THURSDAY)
    expect(day.seconds).toEqual(7200)
  }

  @Test
  fun `should sort rows at one start with Kuku first`() {
    val sihik = RadioProgramme(500, "Sihik", "sihik").apply { id = 1 }
    val tool = RadioProgramme(1_000_001, "Kuum tool", "kuum-tool", station = Station.ARIPAEV).apply { id = 2 }
    val airings = listOf(aired(2, tool, 12), aired(1, sihik, 12), aired(3, tool, 15))
    expect(rows(service("2026-10-29T08:00:00Z", airings, sihik, tool).day(THURSDAY)).map { it.id }).toEqual(listOf(1L, 2L, 3L))
  }

  @Test
  fun `should plan the airings of each station apart`() {
    val sihik = RadioProgramme(500, "Sihik", "sihik", recordEvery = true).apply { id = 1 }
    val tool = RadioProgramme(1_000_001, "Kuum tool", "kuum-tool", recordEvery = true, station = Station.ARIPAEV).apply { id = 2 }
    val day = service("2026-10-29T08:00:00Z", listOf(aired(1, sihik, 12), aired(2, tool, 12)), sihik, tool).day(THURSDAY)
    expect(day.planned.map { it.station }).toEqual(listOf(Station.KUKU, Station.ARIPAEV))
  }

  @Test
  fun `should clip a planned airing to the end of the broadcast day`() {
    expect(planned(listOf(airing(AiringState.PLANNED)), 1800)).toEqual(listOf(PlannedSpan("Keskpäevatund", 0, 1800, Station.KUKU)))
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
