package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.LiveUpdate
import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RadioDigestProgressServiceTest {
  private var now = Instant.parse("2026-11-16T22:00:00Z")

  private val clock =
    object : Clock() {
      override fun getZone(): ZoneId = ZoneId.of("Europe/Tallinn")

      override fun withZone(zone: ZoneId): Clock = this

      override fun instant(): Instant = now
    }

  private val live = mockk<LiveUpdateService>(relaxed = true)
  private val progress = RadioDigestProgressService(live, clock)

  @Test
  fun `should start at choosing the stories with the judge's pace left`() {
    progress.started(DAY)
    expect(progress.current()).toEqual(DigestBuild(DAY, DigestStep.STORIES, secondsLeft = 724))
  }

  @Test
  fun `should list the picked titles while the clips are chosen`() {
    progress.started(DAY)
    progress.judged(listOf("Rünnakud Ust-Lugas", "Valimised"))
    expect(progress.current()?.let { it.step to it.titles }).toEqual(DigestStep.CLIPS to listOf("Rünnakud Ust-Lugas", "Valimised"))
  }

  @Test
  fun `should count the clips chosen`() {
    progress.started(DAY)
    progress.judged(listOf("Üks", "Kaks", "Kolm"))
    progress.chosen()
    progress.chosen()
    expect(progress.current()?.done).toEqual(2)
  }

  @Test
  fun `should keep only the fitted titles while cutting`() {
    progress.started(DAY)
    progress.judged(listOf("Üks", "Kaks", "Kolm"))
    progress.fitted(listOf("Üks", "Kolm"))
    expect(progress.current()?.let { listOf(it.step, it.titles, it.done) }).toEqual(listOf(DigestStep.CUTTING, listOf("Üks", "Kolm"), 0))
  }

  @Test
  fun `should estimate the rest of a step from its pace so far`() {
    progress.started(DAY)
    progress.judged(listOf("Üks", "Kaks", "Kolm", "Neli"))
    now = now.plusSeconds(20)
    progress.chosen()
    expect(progress.current()?.secondsLeft).toEqual(302)
  }

  @Test
  fun `should fill the meter by the share of the build's time spent`() {
    progress.started(DAY)
    now = now.plusSeconds(70)
    progress.judged((1..20).map { "Lugu $it" })
    expect(progress.current()?.percent).toEqual(10)
  }

  @Test
  fun `should show the mix percent while mixing`() {
    progress.started(DAY)
    progress.fitted(listOf("Üks"))
    progress.mixed(0.45)
    expect(progress.current()?.let { it.step to it.done }).toEqual(DigestStep.MIXING to 45)
  }

  @Test
  fun `should announce the mix only at each tenth`() {
    progress.started(DAY)
    listOf(0.02, 0.05, 0.11, 0.15, 0.21).forEach(progress::mixed)
    verify(exactly = 4) { live.publish(LiveUpdate.DIGEST) }
  }

  @Test
  fun `should forget the build once it ends`() {
    progress.started(DAY)
    progress.ended()
    expect(progress.current()).toEqual(null)
  }

  companion object {
    private val DAY = LocalDate.of(2026, 11, 16)
  }
}
