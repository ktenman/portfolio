package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.LiveUpdate
import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

@Service
class RadioReviewService(
  private val radioRenderService: RadioRenderService,
  private val liveUpdateService: LiveUpdateService,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("radio-review").factory())
  private val runs = ConcurrentHashMap<String, Future<*>>()
  private val running = ConcurrentHashMap<String, RenderProgress>()
  private val failures = ConcurrentHashMap<String, EpisodeFailure>()
  private val latest = ConcurrentHashMap<String, Long>()
  private val generations = AtomicLong()

  @Synchronized
  fun review(
    directory: Path,
    keep: List<KeptSpan>,
  ): Boolean {
    if (Files.notExists(directory.resolve(EDIT_LIST_FILE))) return false
    val status = readStatus(directory)
    val spans = spans(Timeline(directory, status.parts), keep).map { KeptSpan(it.first, it.second) }
    val name = directory.fileName.toString()
    save(directory.resolve(REVIEW_FILE), spans)
    failures.remove(name)
    running[name] = RenderProgress(0, ceil(RENDER_PACE * status.seconds / 60).toInt())
    val generation = generations.incrementAndGet()
    latest[name] = generation
    runs[name]?.cancel(true)
    runs[name] = executor.submit { render(directory, spans, generation) }
    liveUpdateService.publish(LiveUpdate.RADIO)
    return true
  }

  fun pending(directory: Path): List<KeptSpan>? = read(directory.resolve(REVIEW_FILE))

  fun progress(name: String): RenderProgress? = running[name]

  fun failure(name: String): EpisodeFailure? = failures[name]

  private fun render(
    directory: Path,
    keep: List<KeptSpan>,
    generation: Long,
  ) {
    val name = directory.fileName.toString()
    val started = System.nanoTime()
    runCatching { rerender(directory, keep) { share -> advanced(name, generation, share, started) } }
      .onSuccess { finished(directory, generation, it) }
      .onFailure { failed(directory, generation, it) }
  }

  @Synchronized
  private fun advanced(
    name: String,
    generation: Long,
    share: Double,
    started: Long,
  ) {
    if (Thread.currentThread().isInterrupted || latest[name] != generation) {
      throw CancellationException("Review render of $name was replaced")
    }
    if (share <= 0) return
    val elapsed = (System.nanoTime() - started) / 1e9
    val percent = min(MOST, (share * 100).roundToInt())
    val before = running.put(name, RenderProgress(percent, ceil(elapsed * (1 - share) / share).toInt().coerceAtLeast(1)))
    if (before == null || percent / STEP > before.percent / STEP) liveUpdateService.publish(LiveUpdate.RADIO)
  }

  @Synchronized
  private fun finished(
    directory: Path,
    generation: Long,
    edit: EditList,
  ) {
    val current = read<EditList>(directory.resolve(EDIT_LIST_FILE)) ?: edit
    val saved =
      current.copy(
        keep = edit.keep,
        durationSeconds = edit.durationSeconds,
        chapters = edit.chapters,
        reviewed = edit.reviewed,
        renderedAt = edit.renderedAt,
      )
    save(directory.resolve(EDIT_LIST_FILE), saved)
    settled(directory, generation)
  }

  @Synchronized
  private fun failed(
    directory: Path,
    generation: Long,
    failure: Throwable,
  ) {
    val name = directory.fileName.toString()
    if (failure is CancellationException || failure is InterruptedException || latest[name] != generation) return
    log.error("Review render of $directory failed", failure)
    val reason = failure.message ?: failure.javaClass.simpleName
    failures[name] = EpisodeFailure(reason, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString())
    settled(directory, generation)
  }

  private fun settled(
    directory: Path,
    generation: Long,
  ) {
    val name = directory.fileName.toString()
    if (latest[name] != generation) return
    if (pending(directory) == read<EditList>(directory.resolve(EDIT_LIST_FILE))?.keep) Files.deleteIfExists(directory.resolve(REVIEW_FILE))
    running.remove(name)
    liveUpdateService.publish(LiveUpdate.RADIO)
  }

  private fun rerender(
    directory: Path,
    keep: List<KeptSpan>,
    progress: (Double) -> Unit,
  ): EditList {
    val edit = checkNotNull(read<EditList>(directory.resolve(EDIT_LIST_FILE))) { "Recording $directory has no edit list to review" }
    val status = readStatus(directory)
    val timeline = Timeline(directory, status.parts)
    val spans = keep.map { it.start to it.end }
    val topics = edit.topics.map { OutlineChapter(it.start, it.title) }
    val marks = chapters(audible(timeline.spans, spans), topics, names(status, edit.found()))
    val temporary = directory.resolve(REVIEWED)
    runCatching { radioRenderService.render(timeline, spans, marks, title(status, edit.found(), clock.zone), temporary, progress) }
      .onFailure { Files.deleteIfExists(temporary) }
      .getOrThrow()
    Files.move(temporary, directory.resolve(EPISODE), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    return reviewed(edit, keep, audible(timeline.spans, spans), marks)
  }

  private fun reviewed(
    edit: EditList,
    keep: List<KeptSpan>,
    kept: List<Pair<Double, Double>>,
    marks: List<Pair<Double, String>>,
  ): EditList =
    edit.copy(
      keep = keep,
      durationSeconds = rounded(kept.sumOf { it.second - it.first }),
      chapters = marks.map { EpisodeChapter(rounded(it.first), it.second) },
      reviewed = true,
      renderedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString(),
    )

  private fun spans(
    timeline: Timeline,
    keep: List<KeptSpan>,
  ): List<Pair<Double, Double>> {
    val spans = keep.map { it.start to min(it.end, timeline.seconds) }
    require(ordered(spans) && spans.all { audible(timeline.spans, listOf(it)).isNotEmpty() }) {
      "Keep list $keep is not ordered audible spans within 0-${timeline.seconds} s"
    }
    return spans
  }

  private fun ordered(spans: List<Pair<Double, Double>>): Boolean =
    spans.isNotEmpty() &&
      spans.all { (start, end) -> start >= 0 && start < end } &&
      spans.zipWithNext().all { (left, right) -> left.second < right.first }

  companion object {
    private const val EPISODE = "episode.m4a"
    private const val REVIEWED = "episode.review.m4a"
    private const val MOST = 99
    private const val STEP = 10
  }
}
