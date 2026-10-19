package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class RadioProgressServiceTest {
  private var now = Instant.parse("2026-10-24T10:00:00Z")

  private val clock =
    object : Clock() {
      override fun getZone(): ZoneId = ZoneOffset.UTC

      override fun withZone(zone: ZoneId): Clock = this

      override fun instant(): Instant = now
    }

  private val service = RadioProgressService(clock)

  private val name = "2026-10-24-1155-keskpaevatund"

  private fun stage(
    step: CutStep,
    seconds: Double,
    read: Int? = null,
    programme: String = PROGRAMME,
  ) = CutStage(programme, step, read, seconds)

  @Test
  fun `should report the read a programme is on with its percent and time left`() {
    service.start(
      name,
      listOf(
        stage(CutStep.SILENCE, 2.0),
        stage(CutStep.READ, 3.5, 1),
        stage(CutStep.READ, 3.5, 2),
        stage(CutStep.READ, 3.5, 3),
        stage(CutStep.BOUNDARIES, 1.0),
        stage(CutStep.RENDER, 10.0),
      ),
    )
    service.next(name, CutStep.SILENCE)
    service.next(name, CutStep.READ)
    now = now.plusSeconds(1)
    expect(service.progress(name)).toEqual(mapOf(PROGRAMME to CutProgress(CutStep.READ, 2, 28, 17)))
  }

  @Test
  fun `should cap a stage that overruns its estimate`() {
    service.start(name, listOf(stage(CutStep.READ, 3.5, 1)))
    now = now.plusSeconds(60)
    expect(service.progress(name)).toEqual(mapOf(PROGRAMME to CutProgress(CutStep.READ, 1, 95, 1)))
  }

  @Test
  fun `should show a programme whose stages are done while the run goes on`() {
    service.start(
      name,
      listOf(
        stage(CutStep.SILENCE, 1.0),
        stage(CutStep.BOUNDARIES, 1.0),
        stage(CutStep.BOUNDARIES, 1.0, programme = "Tarkade klubi"),
        stage(CutStep.RENDER, 1.0, programme = "Tarkade klubi"),
      ),
    )
    service.next(name, CutStep.SILENCE)
    service.next(name, CutStep.BOUNDARIES)
    expect(service.done(name) to service.current()).toEqual(setOf(PROGRAMME) to "Tarkade klubi")
  }

  @Test
  fun `should stay on a stage when told another step is done`() {
    service.start(name, listOf(stage(CutStep.SILENCE, 2.0), stage(CutStep.BOUNDARIES, 1.0)))
    service.next(name, CutStep.DECODE)
    expect(service.progress(name).getValue(PROGRAMME).step).toEqual(CutStep.SILENCE)
  }

  @Test
  fun `should forget a run once it finishes`() {
    service.start(name, listOf(stage(CutStep.SILENCE, 1.0)))
    service.finish(name)
    expect(service.progress(name) to service.current()).toEqual(emptyMap<String, CutProgress>() to null)
  }

  @Test
  fun `should leave the reads out of the plan for a programme with a saved outline`() {
    val status = RecordingStatus(RecordingState.CAPTURED, 0.0, 4200.0, listOf(RecordingPart("raw.mp3", 0.0)), listOf(PROGRAMME))
    expect(stages(status, listOf(PROGRAMME), decode = false) { true }.map { it.step })
      .toEqual(listOf(CutStep.SILENCE, CutStep.BOUNDARIES, CutStep.RENDER))
  }

  @Test
  fun `should plan the decoding three reads and the render of a recording without pcm`() {
    val status = RecordingStatus(RecordingState.CAPTURED, 0.0, 600.0, listOf(RecordingPart("raw.mp3", 0.0)), listOf(PROGRAMME))
    expect(stages(status, listOf(PROGRAMME), decode = true) { false }.map { it.step to it.seconds })
      .toEqual(
        listOf(
          CutStep.DECODE to 3.1,
          CutStep.SILENCE to 0.2,
          CutStep.READ to 3.5,
          CutStep.READ to 3.5,
          CutStep.READ to 3.5,
          CutStep.BOUNDARIES to 1.0,
          CutStep.RENDER to 22.0,
        ),
      )
  }
}
