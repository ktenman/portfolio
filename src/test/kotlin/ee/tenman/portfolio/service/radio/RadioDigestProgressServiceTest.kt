package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.notToThrow
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.LiveUpdate
import ee.tenman.portfolio.exception.DigestBuildInProgressException
import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
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
    progress.started(DigestShow.WEEK, DAY)
    expect(progress.current()).toEqual(DigestBuild(DigestShow.WEEK, DAY, DigestStep.STORIES, secondsLeft = 724))
  }

  @Test
  fun `should expect half the stories and mix for the day show`() {
    progress.started(DigestShow.DAY, DAY)
    expect(progress.current()?.secondsLeft).toEqual(397)
  }

  @Test
  fun `should list the picked titles while the clips are chosen`() {
    progress.started(DigestShow.WEEK, DAY)
    progress.judged(listOf("Rünnakud Ust-Lugas", "Valimised"))
    expect(progress.current()?.let { it.step to it.titles }).toEqual(DigestStep.CLIPS to listOf("Rünnakud Ust-Lugas", "Valimised"))
  }

  @Test
  fun `should count the clips chosen`() {
    progress.started(DigestShow.WEEK, DAY)
    progress.judged(listOf("Üks", "Kaks", "Kolm"))
    progress.chosen()
    progress.chosen()
    expect(progress.current()?.done).toEqual(2)
  }

  @Test
  fun `should keep only the fitted titles while cutting`() {
    progress.started(DigestShow.WEEK, DAY)
    progress.judged(listOf("Üks", "Kaks", "Kolm"))
    progress.fitted(listOf("Üks", "Kolm"))
    expect(progress.current()?.let { listOf(it.step, it.titles, it.done) }).toEqual(listOf(DigestStep.CUTTING, listOf("Üks", "Kolm"), 0))
  }

  @Test
  fun `should estimate the rest of a step from its pace so far`() {
    progress.started(DigestShow.WEEK, DAY)
    progress.judged(listOf("Üks", "Kaks", "Kolm", "Neli"))
    now = now.plusSeconds(20)
    progress.chosen()
    expect(progress.current()?.secondsLeft).toEqual(302)
  }

  @Test
  fun `should fill the meter by the share of the build's time spent`() {
    progress.started(DigestShow.WEEK, DAY)
    now = now.plusSeconds(70)
    progress.judged((1..20).map { "Lugu $it" })
    expect(progress.current()?.percent).toEqual(10)
  }

  @Test
  fun `should show the mix percent while mixing`() {
    progress.started(DigestShow.WEEK, DAY)
    progress.fitted(listOf("Üks"))
    progress.mixed(0.45)
    expect(progress.current()?.let { it.step to it.done }).toEqual(DigestStep.MIXING to 45)
  }

  @Test
  fun `should announce the mix only at each tenth`() {
    progress.started(DigestShow.WEEK, DAY)
    listOf(0.02, 0.05, 0.11, 0.15, 0.21).forEach(progress::mixed)
    verify(exactly = 4) { live.publish(LiveUpdate.DIGEST) }
  }

  @Test
  fun `should forget the build once it ends`() {
    progress.started(DigestShow.WEEK, DAY)
    progress.ended()
    expect(progress.current()).toEqual(null)
  }

  @Test
  fun `should refuse a second build while one runs`() {
    progress.started(DigestShow.WEEK, DAY)
    expect { progress.started(DigestShow.DAY, DAY) }.toThrow<DigestBuildInProgressException>()
  }

  @Test
  fun `should keep the running build when a second one is refused`() {
    progress.started(DigestShow.WEEK, DAY)
    runCatching { progress.started(DigestShow.DAY, DAY) }
    expect(progress.current()?.show).toEqual(DigestShow.WEEK)
  }

  @Test
  fun `should take a new build once the last one ended`() {
    progress.started(DigestShow.WEEK, DAY)
    progress.ended()
    expect { progress.started(DigestShow.DAY, DAY) }.notToThrow()
  }

  @Test
  fun `should hold the reason of a failed build`() {
    val today = LocalDate.now(clock)
    progress.started(DigestShow.WEEK, today)
    progress.failed(DigestShow.WEEK, today, "Mudel ei vastanud õigel ajal")
    expect(progress.held()?.failure).toEqual(DigestFailure("Mudel ei vastanud õigel ajal", 0, 0, null))
  }

  @Test
  fun `should free the claim when a build fails`() {
    val today = LocalDate.now(clock)
    progress.started(DigestShow.WEEK, today)
    progress.failed(DigestShow.WEEK, today, "Mudel ei vastanud õigel ajal")
    expect { progress.started(DigestShow.WEEK, today) }.notToThrow()
  }

  @Test
  fun `should forget the failure once the next build starts`() {
    val today = LocalDate.now(clock)
    progress.failed(DigestShow.WEEK, today, "Mudel ei vastanud õigel ajal")
    progress.started(DigestShow.WEEK, today)
    expect(progress.held()).toEqual(null)
  }

  @Test
  fun `should forget a failure of an earlier day`() {
    progress.failed(DigestShow.WEEK, LocalDate.now(clock), "Mudel ei vastanud õigel ajal")
    now = now.plus(Duration.ofDays(1))
    expect(progress.held()).toEqual(null)
  }

  @Test
  fun `should tell the page when a build fails`() {
    progress.failed(DigestShow.WEEK, LocalDate.now(clock), "Mudel ei vastanud õigel ajal")
    verify { live.publish(LiveUpdate.DIGEST) }
  }

  companion object {
    private val DAY = LocalDate.of(2026, 11, 16)
  }
}
