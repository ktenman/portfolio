package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.domain.LiveUpdate
import ee.tenman.portfolio.service.infrastructure.LiveUpdateService
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

@Service
class RadioProgressService(
  private val clock: Clock,
  private val liveUpdateService: LiveUpdateService,
) {
  private val runs = ConcurrentHashMap<String, CutRun>()

  fun start(
    name: String,
    stages: List<CutStage>,
  ) {
    runs[name] = CutRun(stages, 0, Instant.now(clock))
    liveUpdateService.publish(LiveUpdate.RADIO)
  }

  fun next(
    name: String,
    step: CutStep,
  ) {
    val before = runs[name]
    val after =
      runs.computeIfPresent(name) { _, run ->
        if (run.stages.getOrNull(run.index)?.step == step) run.copy(index = run.index + 1, since = Instant.now(clock)) else run
      }
    if (after !== before) liveUpdateService.publish(LiveUpdate.RADIO)
  }

  fun finish(name: String) {
    runs.remove(name) ?: return
    liveUpdateService.publish(LiveUpdate.RADIO)
  }

  fun programmeCuts(
    directory: Path,
    status: RecordingStatus,
    edit: EditList?,
    failed: Boolean,
  ): List<ProgrammeCuts> {
    val name = directory.fileName.toString()
    val found = edit.found() + done(name)
    val progress = progress(name)
    val transcript =
      if (Files.exists(directory.resolve(TRANSCRIPT_FILE))) null else transcriptProgress(directory, status, Instant.now(clock))
    val after = runs[name]?.let { it.stages.getOrNull(it.index)?.programme } ?: current()
    val first = status.programmes.firstOrNull { it !in found }
    return status.programmes.map { programme ->
      when {
        programme in found -> ProgrammeCuts(programme, ProgrammeCutState.FOUND)
        failed -> ProgrammeCuts(programme, ProgrammeCutState.FAILED)
        status.state == RecordingState.FAILED -> ProgrammeCuts(programme, ProgrammeCutState.NONE)
        transcript != null && programme == first -> ProgrammeCuts(programme, ProgrammeCutState.WAITING, transcript = transcript)
        transcript != null -> ProgrammeCuts(programme, ProgrammeCutState.QUEUED, after = first)
        programme in progress -> ProgrammeCuts(programme, ProgrammeCutState.FINDING, progress = progress[programme])
        else -> ProgrammeCuts(programme, ProgrammeCutState.QUEUED, after = after)
      }
    }
  }

  fun current(): String? = runs.values.firstNotNullOfOrNull { it.stages.getOrNull(it.index)?.programme }

  fun done(name: String): Set<String> {
    val run = runs[name] ?: return emptySet()
    val ahead = run.stages.drop(run.index).map { it.programme }
    return run.stages
      .map { it.programme }
      .filterNot { it in ahead }
      .toSet()
  }

  fun progress(name: String): Map<String, CutProgress> {
    val run = runs[name] ?: return emptyMap()
    val stage = run.stages.getOrNull(run.index) ?: return emptyMap()
    val own = run.stages.withIndex().filter { it.value.programme == stage.programme }
    val cap = STAGE_CAP * stage.seconds
    val elapsed = min(Duration.between(run.since, Instant.now(clock)).toMillis() / 1000.0, cap)
    val finished = own.filter { it.index < run.index }.sumOf { it.value.seconds } + elapsed
    val total = own.sumOf { it.value.seconds }
    val percent = (finished / total * 100).roundToInt().coerceAtMost(MOST)
    val left = ceil(total - finished).toInt().coerceAtLeast(1)
    return mapOf(stage.programme to CutProgress(stage.step, stage.read, percent, left, (cap - elapsed).toInt()))
  }

  companion object {
    private const val STAGE_CAP = 0.95
    private const val MOST = 99
  }
}
