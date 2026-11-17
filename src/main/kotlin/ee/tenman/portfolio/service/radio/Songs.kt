package ee.tenman.portfolio.service.radio

import java.nio.file.Path

const val SOUNDS_FILE = "sounds.json"
private const val STRIDE = 0.5
private const val ADJACENT = STRIDE * 1.5
private const val SPEECH = 0.3
private const val MUSIC = 0.5
private const val SEED = 2.0
private const val BRIDGE = 3.0
private const val QUIET = 15.0
private const val WORD = 2.0
private const val SONG = 10.0
private const val SLIVER = 3.0

data class SoundFrame(
  val at: Double,
  val speech: Double,
  val music: Double,
)

data class Sounds(
  val frames: List<SoundFrame>,
)

data class Heard(
  val frames: List<SoundFrame>,
  val words: List<TranscriptWord>,
)

fun readSounds(directory: Path): List<SoundFrame> = read<Sounds>(directory.resolve(SOUNDS_FILE))?.frames.orEmpty()

fun songs(
  frames: List<SoundFrame>,
  keep: List<Pair<Double, Double>>,
  words: List<TranscriptWord>,
): List<Pair<Double, Double>> {
  val spoken = words.filter { word -> word.end - word.start <= WORD }
  val sung =
    bridged(runs(frames).filter { length(it) >= SEED }, frames.filter { it.speech >= SPEECH }.map { it.at })
      .filter { length(it) >= SONG }
      .map { unsplit(it, spoken) }
  return merged(sung + jingles(frames.sortedBy { it.at }, spoken))
    .flatMap { song -> keep.map { maxOf(song.first, it.first) to minOf(song.second, it.second) } }
    .filter { it.second - it.first >= SLIVER }
}

private fun jingles(
  frames: List<SoundFrame>,
  words: List<TranscriptWord>,
): List<Pair<Double, Double>> =
  words
    .sortedBy { it.start }
    .zipWithNext { said, next -> said.end to next.start }
    .filter { it.second - it.first >= SLIVER }
    .mapNotNull { gap -> jingle(frames.filter { it.at >= gap.first && it.at < gap.second }) }

private fun jingle(frames: List<SoundFrame>): Pair<Double, Double>? {
  val first = frames.indexOfFirst(::musical)
  if (first < 0) return null
  val music = frames.subList(first, frames.indexOfLast(::musical) + 1)
  if (music.count(::musical) * 2 < music.size) return null
  return music.first().at to music.last().at
}

private fun merged(cuts: List<Pair<Double, Double>>): List<Pair<Double, Double>> =
  cuts.sortedBy { it.first }.fold(emptyList()) { found, cut ->
    val last = found.lastOrNull()
    if (last == null || cut.first > last.second) return@fold found + cut
    found.dropLast(1) + (last.first to maxOf(last.second, cut.second))
  }

fun without(
  keep: List<Pair<Double, Double>>,
  songs: List<Pair<Double, Double>>,
): List<Pair<Double, Double>> =
  keep.flatMap { (start, end) ->
    val inside = songs.filter { it.first >= start && it.second <= end }.sortedBy { it.first }
    (listOf(start) + inside.flatMap { listOf(it.first, it.second) } + end)
      .chunked(2)
      .map { it[0] to it[1] }
      .filter { it.second > it.first }
  }

private fun musical(frame: SoundFrame): Boolean = frame.music >= MUSIC && frame.speech < SPEECH

private fun length(run: Pair<Double, Double>): Double = run.second - run.first + STRIDE

private fun runs(frames: List<SoundFrame>): List<Pair<Double, Double>> =
  frames
    .filter(::musical)
    .sortedBy { it.at }
    .fold(emptyList()) { found, frame ->
      val last = found.lastOrNull()
      if (last == null || frame.at - last.second >= ADJACENT) return@fold found + (frame.at to frame.at)
      found.dropLast(1) + (last.first to frame.at)
    }

private fun bridged(
  seeds: List<Pair<Double, Double>>,
  talk: List<Double>,
): List<Pair<Double, Double>> =
  seeds.fold(emptyList()) { found, seed ->
    val last = found.lastOrNull()
    if (last != null && joined(last, seed, talk)) found.dropLast(1) + (last.first to seed.second) else found + seed
  }

private fun joined(
  last: Pair<Double, Double>,
  seed: Pair<Double, Double>,
  talk: List<Double>,
): Boolean {
  val gap = seed.first - last.second - STRIDE
  return gap <= BRIDGE || gap <= QUIET && talk.none { it > last.second && it < seed.first }
}

private fun unsplit(
  song: Pair<Double, Double>,
  words: List<TranscriptWord>,
): Pair<Double, Double> {
  val start = words.filter { it.start < song.first && it.end > song.first }.maxOfOrNull { it.end } ?: song.first
  val end = words.filter { it.start < song.second && it.end > song.second }.minOfOrNull { it.start } ?: song.second
  return start to end
}
