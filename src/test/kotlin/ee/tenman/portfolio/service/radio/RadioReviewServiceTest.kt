package ee.tenman.portfolio.service.radio

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.LiveUpdate
import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class RadioReviewServiceTest {
  @TempDir
  lateinit var directory: Path

  private val updates = mockk<LiveUpdateService>(relaxed = true)

  private val clock = Clock.fixed(Instant.parse("2026-10-17T11:10:00Z"), ZoneOffset.UTC)

  private val machine = listOf(Boundary("show starts", 4.7, "transcript only", true), Boundary("show ends", 57.2, "digital silence", false))

  private fun service(render: RadioRenderService = RadioRenderService()) = RadioReviewService(render, updates, clock)

  private fun name(): String = directory.fileName.toString()

  private fun recorded(parts: List<FixturePart> = listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 60)))) {
    writeRecording(directory, parts, if (parts.size > 1) "partial" else "captured")
    Files.writeString(directory.resolve("episode.m4a"), "vana")
    val outline = OutlineSummary(null, 3, 3, 3)
    val edit =
      EditList(listOf(KeptSpan(4.7, 57.2)), 52.5, readStatus(directory).parts, machine, emptyList(), outline, emptyList(), false, "")
    save(directory.resolve("edit-list.json"), edit)
  }

  private fun edit(): EditList = JsonMapperFactory.instance.readValue(Files.readString(directory.resolve("edit-list.json")))

  private fun waitFor(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
    while (!condition()) {
      check(System.nanoTime() < deadline) { "Timed out waiting for the review render" }
      Thread.sleep(20)
    }
  }

  private fun blocking(
    latch: CountDownLatch,
    share: Double = 0.0,
  ) = mockk<RadioRenderService> {
    every { render(any(), any(), any(), any(), any(), any()) } answers {
      arg<(Double) -> Unit>(5)(share)
      latch.await(10, TimeUnit.SECONDS)
    }
  }

  @Test
  fun `should render the reviewed keep list and mark the edit list reviewed`() {
    recorded()
    service().review(directory, listOf(KeptSpan(10.0, 20.0), KeptSpan(30.0, 40.0)))
    waitFor { edit().reviewed }
    val duration = probe(directory.resolve("episode.m4a"))["format"]["duration"].asString().toDouble()
    expect(abs(duration - 20) <= 0.1).toEqual(true)
  }

  @Test
  fun `should keep the machine boundaries when the cuts are reviewed`() {
    recorded()
    service().review(directory, listOf(KeptSpan(10.0, 20.0)))
    waitFor { edit().reviewed }
    expect(edit().boundaries).toEqual(machine)
  }

  @Test
  fun `should end a keep list that reaches past the recording at its last sample`() {
    recorded()
    service().review(directory, listOf(KeptSpan(50.0, 60.04)))
    waitFor { edit().reviewed }
    expect(edit().keep).toEqual(listOf(KeptSpan(50.0, 60.0)))
  }

  @Test
  fun `should reject a keep list whose spans overlap`() {
    recorded()
    expect { service().review(directory, listOf(KeptSpan(10.0, 30.0), KeptSpan(20.0, 40.0))) }
      .toThrow<IllegalArgumentException>()
      .messageToContain("Keep list")
  }

  @Test
  fun `should reject a keep list whose spans touch`() {
    recorded()
    expect { service().review(directory, listOf(KeptSpan(10.0, 20.0), KeptSpan(20.0, 30.0))) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should reject a kept span that no part covers`() {
    recorded(
      listOf(
        FixturePart("raw.mp3", 0.0, taped(directory, "raw", 30)),
        FixturePart("raw-part-002.mp3", 40.0, taped(directory, "raw-part-002", 20)),
      ),
    )
    expect { service().review(directory, listOf(KeptSpan(10.0, 20.0), KeptSpan(32.0, 38.0))) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should reject an empty keep list`() {
    recorded()
    expect { service().review(directory, emptyList()) }.toThrow<IllegalArgumentException>()
  }

  @Test
  fun `should find no review for a recording without an edit list`() {
    writeRecording(directory, listOf(FixturePart("raw.mp3", 0.0, taped(directory, "raw", 60))))
    expect(service().review(directory, listOf(KeptSpan(10.0, 20.0)))).toEqual(false)
  }

  @Test
  fun `should save the decisions before the episode is rendered`() {
    recorded()
    val latch = CountDownLatch(1)
    val service = service(blocking(latch))
    service.review(directory, listOf(KeptSpan(10.0, 20.0)))
    val saved = service.pending(directory) to edit().reviewed
    latch.countDown()
    expect(saved).toEqual(listOf(KeptSpan(10.0, 20.0)) to false)
  }

  @Test
  fun `should report the render's percent while it runs`() {
    recorded()
    val latch = CountDownLatch(1)
    val service = service(blocking(latch, 0.57))
    service.review(directory, listOf(KeptSpan(10.0, 20.0)))
    waitFor { service.progress(name())?.percent == 57 }
    val percent = service.progress(name())?.percent
    latch.countDown()
    expect(percent).toEqual(57)
  }

  @Test
  fun `should render the newest decisions when they arrive during a render`() {
    recorded()
    val calls = AtomicInteger()
    val render =
      mockk<RadioRenderService> {
        every { render(any(), any(), any(), any(), any(), any()) } answers {
          if (calls.incrementAndGet() == 1) {
            repeat(1000) {
              arg<(Double) -> Unit>(5)(0.1)
              Thread.sleep(10)
            }
          }
          Files.writeString(arg<Path>(4), "uus")
        }
      }
    val service = service(render)
    service.review(directory, listOf(KeptSpan(10.0, 20.0)))
    waitFor { service.progress(name()) != null }
    service.review(directory, listOf(KeptSpan(30.0, 40.0)))
    waitFor { edit().reviewed }
    expect(edit().keep).toEqual(listOf(KeptSpan(30.0, 40.0)))
  }

  @Test
  fun `should report a failed render and keep the previous episode`() {
    recorded()
    val render =
      mockk<RadioRenderService> {
        every { render(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("FFmpeg exited with 1")
      }
    val service = service(render)
    service.review(directory, listOf(KeptSpan(10.0, 20.0)))
    waitFor { service.failure(name()) != null }
    expect(service.failure(name())?.reason to Files.readString(directory.resolve("episode.m4a")))
      .toEqual("FFmpeg exited with 1" to "vana")
  }

  @Test
  fun `should announce a radio update when a render finishes`() {
    recorded()
    val render =
      mockk<RadioRenderService> {
        every { render(any(), any(), any(), any(), any(), any()) } answers { Files.writeString(arg<Path>(4), "uus") }
      }
    val service = service(render)
    service.review(directory, listOf(KeptSpan(10.0, 20.0)))
    waitFor { service.pending(directory) == null && service.progress(name()) == null }
    verify(atLeast = 2) { updates.publish(LiveUpdate.RADIO) }
  }
}
