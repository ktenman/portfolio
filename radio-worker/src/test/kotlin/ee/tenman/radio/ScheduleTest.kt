package ee.tenman.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

private class Week(
  var now: Double,
  private val until: Double,
) {
  fun clock() = now

  fun sleep(seconds: Double): Boolean {
    now += seconds
    return now >= until
  }
}

private data class Call(
  val directory: String,
  val started: Double,
  val seconds: Double,
  val programmes: List<String>,
  val slot: Slot?,
  val at: Double,
)

private val saturday = Capture("2026-10-24-keskpaevatund", listOf("Keskpäevatund"), 1792832100, 1792836300)
private val nextSaturday = Capture("2026-10-31-keskpaevatund", listOf("Keskpäevatund"), 1793440500, 1793444700)

private fun follow(
  tmp: Path,
  week: Week,
  sleep: (Double) -> Boolean = week::sleep,
): Pair<Int, List<Call>> {
  val calls = mutableListOf<Call>()
  val code =
    planned(
      tmp,
      { directory, started, seconds, programmes, slot ->
        calls += Call(directory.fileName.toString(), started, seconds, programmes, slot, week.now)
        RecordingState.CAPTURED
      },
      week::clock,
      sleep,
    )
  return Pair(code, calls)
}

class ScheduleTest {
  @Test
  fun `records a capture into its directory until its end`(
    @TempDir tmp: Path,
  ) {
    write(tmp.resolve("plan.json"), listOf(nextSaturday, saturday))
    val calls = follow(tmp, Week(1792821600.0, 1793444700.0)).second
    expect(calls).toEqual(listOf(Call("2026-10-24-keskpaevatund", 1792832100.0, 4200.0, listOf("Keskpäevatund"), null, 1792832100.0)))
  }

  @Test
  fun `records the next capture once the last one has ended`(
    @TempDir tmp: Path,
  ) {
    write(tmp.resolve("plan.json"), listOf(saturday, nextSaturday))
    val calls = follow(tmp, Week(1792836300.0, 1793444700.0)).second
    expect(calls).toEqual(listOf(Call("2026-10-31-keskpaevatund", 1793440500.0, 4200.0, listOf("Keskpäevatund"), null, 1793440500.0)))
  }

  @Test
  fun `hands the recorder the air slot of the capture`(
    @TempDir tmp: Path,
  ) {
    write(tmp.resolve("plan.json"), listOf(saturday.copy(slot = Slot(1792832400, 1792836000))))
    expect(follow(tmp, Week(1792821600.0, 1793444700.0)).second.single().slot).toEqual(Slot(1792832400, 1792836000))
  }

  @Test
  fun `started during a capture records it from its start`(
    @TempDir tmp: Path,
  ) {
    write(tmp.resolve("plan.json"), listOf(saturday))
    val call = follow(tmp, Week(1792834500.0, 1793444700.0)).second.single()
    expect(Pair(call.started, call.seconds)).toEqual(Pair(1792832100.0, 4200.0))
  }

  @Test
  fun `started over an hour into a capture records its last hour`(
    @TempDir tmp: Path,
  ) {
    write(tmp.resolve("plan.json"), listOf(saturday))
    val call = follow(tmp, Week(1792836000.0, 1793444700.0)).second.single()
    expect(Pair(call.started, call.seconds)).toEqual(Pair(1792832400.0, 3900.0))
  }

  @Test
  fun `picks up a capture added while it waits`(
    @TempDir tmp: Path,
  ) {
    val week = Week(1792821600.0, 1793444700.0)
    val calls =
      follow(tmp, week) { seconds ->
        week.sleep(seconds).also { if (week.now >= 1792828800.0) write(tmp.resolve("plan.json"), listOf(saturday)) }
      }.second
    expect(calls.single().at).toEqual(1792832100.0)
  }

  @Test
  fun `records nothing without a plan`(
    @TempDir tmp: Path,
  ) {
    expect(follow(tmp, Week(1792821600.0, 1792836600.0))).toEqual(Pair(130, emptyList()))
  }

  @Test
  fun `records nothing from a plan it cannot read`(
    @TempDir tmp: Path,
  ) {
    Files.writeString(tmp.resolve("plan.json"), "{ pooleli")
    expect(follow(tmp, Week(1792821600.0, 1792836600.0))).toEqual(Pair(130, emptyList()))
  }

  @Test
  fun `ends with the capture after a failed recording`(
    @TempDir tmp: Path,
  ) {
    write(tmp.resolve("plan.json"), listOf(saturday))
    val week = Week(1792821600.0, 1793444700.0)
    val code = planned(tmp, { _, _, _, _, _ -> error("Kuku is off air") }, week::clock, week::sleep)
    expect(Pair(code, week.now)).toEqual(Pair(0, 1792836300.0))
  }

  @Test
  fun `ends once a capture ends`(
    @TempDir tmp: Path,
  ) {
    write(tmp.resolve("plan.json"), listOf(saturday))
    val week = Week(1792821600.0, 1793444700.0)
    val code = follow(tmp, week).first
    expect(Pair(code, week.now)).toEqual(Pair(0, 1792836300.0))
  }

  @Test
  fun `leaves a heartbeat until its next check`(
    @TempDir tmp: Path,
  ) {
    follow(tmp, Week(1792821600.0, 1792821660.0))
    expect(read<Heartbeat>(tmp.resolve("heartbeat.json"))).toEqual(Heartbeat(1792821600.0, 1792821660.0))
  }

  @Test
  fun `leaves a heartbeat until the capture ends`(
    @TempDir tmp: Path,
  ) {
    write(tmp.resolve("plan.json"), listOf(saturday))
    val week = Week(1792834500.0, 1793444700.0)
    val beats = mutableListOf<Heartbeat>()
    planned(
      tmp,
      { _, _, _, _, _ ->
        beats += read<Heartbeat>(tmp.resolve("heartbeat.json"))
        RecordingState.CAPTURED
      },
      week::clock,
      week::sleep,
    )
    expect(beats.single()).toEqual(Heartbeat(1792834500.0, 1792836300.0))
  }
}
