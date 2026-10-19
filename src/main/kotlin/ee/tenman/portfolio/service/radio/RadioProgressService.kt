package ee.tenman.portfolio.service.radio

import org.springframework.stereotype.Service
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
) {
  private val runs = ConcurrentHashMap<String, CutRun>()

  fun start(
    name: String,
    stages: List<CutStage>,
  ) {
    runs[name] = CutRun(stages, 0, Instant.now(clock))
  }

  fun next(
    name: String,
    step: CutStep,
  ) {
    runs.computeIfPresent(name) { _, run ->
      if (run.stages.getOrNull(run.index)?.step == step) run.copy(index = run.index + 1, since = Instant.now(clock)) else run
    }
  }

  fun finish(name: String) {
    runs.remove(name)
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
    val elapsed = min(Duration.between(run.since, Instant.now(clock)).toMillis() / 1000.0, STAGE_CAP * stage.seconds)
    val finished = own.filter { it.index < run.index }.sumOf { it.value.seconds } + elapsed
    val total = own.sumOf { it.value.seconds }
    val percent = (finished / total * 100).roundToInt().coerceAtMost(MOST)
    return mapOf(stage.programme to CutProgress(stage.step, stage.read, percent, ceil(total - finished).toInt().coerceAtLeast(1)))
  }

  companion object {
    private const val STAGE_CAP = 0.95
    private const val MOST = 99
  }
}
