package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RadioDayTest {
  private fun recording(
    from: Long,
    to: Long,
  ) = AiringRecording("2026-10-17-1200-keskpaevatund", "Keskpäevatund", 0, 4200, from, to)

  private fun airing(state: AiringState) = DayAiring(1, "Keskpäevatund", null, 0, 3600, state, false, false, null, null)

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
    val takes = listOf(Take("a", "Ilmaparandaja", 0, 200, false), Take("b", "Keskpäevatund", 3400, 4000, false))
    expect(covered(100, 3700, takes)).toEqual(AiringRecording("b", "Keskpäevatund", 3400, 4000, 3400, 3700))
  }

  @Test
  fun `should not count a recording that touches an airing for under a minute`() {
    expect(covered(1000, 4600, listOf(Take("a", "Keskpäevatund", 0, 1030, false)))).toEqual(null)
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
      listOf(Take("b", "Õhtutund", 900, 1100, false), Take("a", "Keskpäevatund", 50, 150, false), Take("c", "Öötund", 1200, 1300, false))
    expect(spans(takes, 100, 1000)).toEqual(listOf(RecordedSpan(100, 150, false), RecordedSpan(900, 1000, false)))
  }

  @Test
  fun `should mark the span of a take still being recorded`() {
    val takes = listOf(Take("a", "Kuku", 50, 150, false), Take("b", "Kuku", 900, 1100, true))
    expect(spans(takes, 100, 1000)).toEqual(listOf(RecordedSpan(100, 150, false), RecordedSpan(900, 1000, true)))
  }
}
