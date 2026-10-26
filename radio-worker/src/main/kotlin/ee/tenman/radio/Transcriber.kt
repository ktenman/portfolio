package ee.tenman.radio

import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val BLOCK = 10 * RATE

fun transcribe(
  directory: Path,
  load: () -> Ears,
  tagger: Path,
  alive: () -> Boolean,
  stop: CountDownLatch,
) {
  if (Files.exists(directory.resolve("transcript.json"))) return
  val journal = directory.resolve("transcript.jsonl")
  val session = Session(directory, load(), journal, resume(journal), alive, stop)
  val parts = session.follow() ?: return
  listen(directory, parts, tagger)
  write(directory.resolve("transcript.json"), session.transcript())
}

private fun resume(journal: Path): MutableList<Chunk> {
  if (Files.notExists(journal)) return mutableListOf()
  val lines = String(Files.readAllBytes(journal), Charsets.UTF_8).split("\n")
  val kept = lines.takeWhile { line -> runCatching { mapper.readValue<Chunk>(line) }.isSuccess }
  save(journal, kept.joinToString("") { "$it\n" })
  return kept.map { mapper.readValue<Chunk>(it) }.toMutableList()
}

private class Session(
  private val directory: Path,
  private val ears: Ears,
  private val journal: Path,
  private val chunks: MutableList<Chunk>,
  private val alive: () -> Boolean,
  private val stop: CountDownLatch,
) {
  private val stopped: Boolean
    get() = stop.count == 0L
  private var index = 0
  private var hearing: Hearing? = null

  tailrec fun follow(): List<Part>? {
    if (stopped) return null
    val living = alive()
    val status = read<Status>(directory.resolve("status.json"))
    val over = status.state != RecordingState.RECORDING
    check(over || living) { "The recorder stopped while ${directory.resolve("status.json")} still says recording" }
    if (index == status.parts.size && over) return status.parts
    if (!advance(status.parts, over)) stop.await(1, TimeUnit.SECONDS)
    return follow()
  }

  fun transcript(): Transcript {
    val segments = chunks.flatMap { it.segments }
    return Transcript(ears.name, ears.revision, segments.map { Line(it.start, it.end, it.text) }, segments.flatMap { it.words })
  }

  private fun advance(
    parts: List<Part>,
    over: Boolean,
  ): Boolean {
    if (index == parts.size) return false
    val part = parts[index]
    val last = chunks.lastOrNull { it.part == part.file }
    if (last?.final == true) return next()
    val finished = over || part.durationSeconds != null
    val current = hearing ?: Hearing(part, last?.to ?: overlap(directory, parts, index)).also { hearing = it }
    val heard = current.hear()
    if (stopped || !finished) return heard
    current.end()
    return next()
  }

  private fun next(): Boolean {
    index += 1
    hearing = null
    return true
  }

  private fun add(chunk: Chunk) {
    append(journal, chunk)
    chunks += chunk
  }

  private inner class Hearing(
    private val part: Part,
    private val start: Double,
  ) {
    private val pcm = pcm(directory, part)
    private val shift = part.offsetSeconds + start
    private var position = Math.round(start * RATE)
    private var cut = round3(start)
    private var ear: Ear? = null

    fun hear(): Boolean = generateSequence { block() }.onEach { feed(it) }.count() > 0

    fun end() {
      val segments = ear?.use { it.end() } ?: emptyList()
      add(Chunk(part.file, cut, round3(maxOf(cut, seconds(pcm))), true, shifted(segments)))
    }

    private fun block(): FloatArray? {
      val count = minOf(samples(pcm) - position, BLOCK.toLong()).toInt()
      if (stopped || count <= 0) return null
      return floats(pcm, position, count).also { position += count }
    }

    private fun feed(audio: FloatArray) {
      val utterances = (ear ?: ears.open().also { ear = it }).hear(audio)
      utterances.forEach { utterance ->
        val to = round3(start + utterance.to)
        add(Chunk(part.file, cut, to, false, shifted(utterance.segments)))
        cut = to
      }
    }

    private fun shifted(segments: List<Segment>): List<Segment> =
      segments.map { segment ->
        Segment(at(segment.start), at(segment.end), segment.text, segment.words.map { Word(at(it.start), at(it.end), it.word) })
      }

    private fun at(time: Double): Double = round3(shift + time)
  }
}
