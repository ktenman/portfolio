package ee.tenman.portfolio.service.radio

import java.time.Instant

const val OUTLINE_READS = 3
private const val DECODE_PACE = 0.31
private const val SILENCE_PACE = 0.02
private const val READ_SECONDS = 3.5
private const val BOUNDARIES_SECONDS = 1.0
private const val RENDER_PACE = 2.2

enum class CutStep {
  DECODE,
  SILENCE,
  READ,
  BOUNDARIES,
  RENDER,
}

data class CutStage(
  val programme: String,
  val step: CutStep,
  val read: Int?,
  val seconds: Double,
)

data class CutRun(
  val stages: List<CutStage>,
  val index: Int,
  val since: Instant,
)

data class CutProgress(
  val step: CutStep,
  val read: Int?,
  val percent: Int,
  val secondsLeft: Int,
)

data class TranscriptProgress(
  val writtenSeconds: Double,
  val totalSeconds: Double,
  val percent: Int,
  val secondsLeft: Int,
)

fun stages(
  status: RecordingStatus,
  todo: List<String>,
  decode: Boolean,
  saved: (String) -> Boolean,
): List<CutStage> {
  val minutes = status.seconds / 60
  val first = todo.first()
  val opening =
    listOfNotNull(
      CutStage(first, CutStep.DECODE, null, DECODE_PACE * minutes).takeIf { decode },
      CutStage(first, CutStep.SILENCE, null, SILENCE_PACE * minutes),
    )
  val found = todo.flatMap { reads(it, saved(it)) + CutStage(it, CutStep.BOUNDARIES, null, BOUNDARIES_SECONDS) }
  return opening + found + CutStage(todo.last(), CutStep.RENDER, null, RENDER_PACE * minutes)
}

private fun reads(
  programme: String,
  saved: Boolean,
): List<CutStage> = if (saved) emptyList() else (1..OUTLINE_READS).map { CutStage(programme, CutStep.READ, it, READ_SECONDS) }
