package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.notToThrow
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.exception.DigestBuildInProgressException
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RadioDigestRebuildServiceTest {
  private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneId.of("Europe/Tallinn"))

  private val digests = mockk<RadioDigestService> { justRun { rebuild(any(), any()) } }

  private val progress = RadioDigestProgressService(mockk(relaxed = true), clock)

  private val positions = mockk<RadioPositionService>(relaxed = true)

  private val service = RadioDigestRebuildService(digests, progress, positions, clock)

  @Test
  fun `should refuse to rebuild while another digest builds`() {
    progress.started(DigestShow.WEEK, TODAY)
    expect { service.rebuild(DigestShow.DAY) }.toThrow<DigestBuildInProgressException>()
  }

  @Test
  fun `should build today's digest in the background`() {
    service.rebuild(DigestShow.DAY)
    verify(timeout = 10_000) { digests.rebuild(DigestShow.DAY, TODAY) }
  }

  @Test
  fun `should forget where listeners stopped in the replaced digest`() {
    service.rebuild(DigestShow.DAY)
    verify(timeout = 10_000) { positions.forget("day-in-radio-2026-11-16") }
  }

  @Test
  fun `should show the build as running while it is made`() {
    val latch = CountDownLatch(1)
    every { digests.rebuild(DigestShow.DAY, TODAY) } answers { latch.await(10, TimeUnit.SECONDS) }
    service.rebuild(DigestShow.DAY)
    val step = progress.current()?.step
    latch.countDown()
    expect(step).toEqual(DigestStep.STORIES)
  }

  @Test
  fun `should free the claim once the digest is written`() {
    service.rebuild(DigestShow.DAY)
    await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(10)).until { progress.current() == null }
    expect { progress.started(DigestShow.WEEK, TODAY) }.notToThrow()
  }

  @Test
  fun `should hold only the first line of a long failure`() {
    val error = IllegalStateException("FFmpeg lõpetas koodiga 1: \n[mp3 @ 0x1] Päis puudub")
    expect(failure(error)?.reason).toEqual("FFmpeg lõpetas koodiga 1:")
  }

  @Test
  fun `should name the error when a failed rebuild has no message`() {
    expect(failure(IllegalStateException())?.reason).toEqual("IllegalStateException")
  }

  private fun failure(error: Throwable): DigestFailure? {
    every { digests.rebuild(DigestShow.DAY, TODAY) } throws error
    service.rebuild(DigestShow.DAY)
    await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(10)).until { progress.held() != null }
    return progress.held()?.failure
  }

  companion object {
    private val TODAY = LocalDate.of(2026, 11, 16)
  }
}
