package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.LiveUpdate
import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Service
class RadioDigestProgressService(
  private val liveUpdateService: LiveUpdateService,
  private val clock: Clock,
) {
  private val tracked = AtomicReference<DigestTrack?>()

  fun current(): DigestBuild? = tracked.get()?.build

  fun started(date: LocalDate) {
    val now = clock.instant()
    tracked.set(estimated(DigestTrack(DigestBuild(date, DigestStep.STORIES), now, now), now))
    liveUpdateService.publish(LiveUpdate.DIGEST)
  }

  fun judged(titles: List<String>) = moved(DigestStep.CLIPS, titles)

  fun chosen() = counted()

  fun fitted(titles: List<String>) = moved(DigestStep.CUTTING, titles)

  fun cut() = counted()

  fun mixed(share: Double) {
    val before = tracked.get() ?: return
    val done = (share * 100).roundToInt().coerceIn(0, MOST)
    val now = clock.instant()
    val mixing = before.build.step == DigestStep.MIXING
    val stepAt = if (mixing) before.stepAt else now
    tracked.set(estimated(before.copy(build = before.build.copy(step = DigestStep.MIXING, done = done), stepAt = stepAt), now))
    if (!mixing || done / TENTH > before.build.done / TENTH) liveUpdateService.publish(LiveUpdate.DIGEST)
  }

  fun ended() {
    tracked.set(null)
    liveUpdateService.publish(LiveUpdate.DIGEST)
  }

  private fun moved(
    step: DigestStep,
    titles: List<String>,
  ) {
    val now = clock.instant()
    changed(now) { it.copy(build = it.build.copy(step = step, titles = titles, done = 0), stepAt = now) }
  }

  private fun counted() = changed(clock.instant()) { it.copy(build = it.build.copy(done = it.build.done + 1)) }

  private fun changed(
    now: Instant,
    edit: (DigestTrack) -> DigestTrack,
  ) {
    tracked.updateAndGet { track -> track?.let { estimated(edit(it), now) } } ?: return
    liveUpdateService.publish(LiveUpdate.DIGEST)
  }

  private fun estimated(
    track: DigestTrack,
    now: Instant,
  ): DigestTrack {
    val left = max(1.0, remaining(track.build, seconds(track.stepAt, now)))
    val spent = seconds(track.startedAt, now)
    val percent = (100 * spent / (spent + left)).roundToInt().coerceIn(0, MOST)
    return track.copy(build = track.build.copy(percent = percent, secondsLeft = ceil(left).toInt()))
  }

  private fun remaining(
    build: DigestBuild,
    spent: Double,
  ): Double =
    when (build.step) {
      DigestStep.STORIES -> max(0.0, JUDGE_SECONDS - spent) + (TYPICAL_STORIES * (CLIP_SECONDS + CUT_SECONDS) + MIX_SECONDS)
      DigestStep.CLIPS -> paced(build, spent, CLIP_SECONDS) + (build.titles.size * CUT_SECONDS + MIX_SECONDS)
      DigestStep.CUTTING -> paced(build, spent, CUT_SECONDS) + MIX_SECONDS
      DigestStep.MIXING -> if (build.done > 0) spent * (100 - build.done) / build.done else MIX_SECONDS
      null -> 0.0
    }

  private fun paced(
    build: DigestBuild,
    spent: Double,
    pace: Double,
  ): Double = (build.titles.size - build.done) * if (build.done > 0) spent / build.done else pace

  private fun seconds(
    from: Instant,
    to: Instant,
  ): Double = Duration.between(from, to).toMillis() / 1000.0

  companion object {
    private const val JUDGE_SECONDS = 70.0
    private const val CLIP_SECONDS = 7.0
    private const val CUT_SECONDS = 17.0
    private const val MIX_SECONDS = RENDER_PACE * 60
    private const val TYPICAL_STORIES = 20
    private const val MOST = 99
    private const val TENTH = 10
  }
}
