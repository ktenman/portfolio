package ee.tenman.radio

import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.nio.file.Path

private const val LAG = 0.4
private const val TAIL = 0.6
private const val BLOCK = RATE / 5
private const val STEP = 0.64
private val STOPS = setOf(".", "?", "!")

interface Ears {
  val name: String

  fun open(): Ear
}

interface Ear : AutoCloseable {
  fun hear(samples: FloatArray): List<Utterance>

  fun end(): List<Segment>
}

data class Utterance(
  val to: Double,
  val segments: List<Segment>,
)

class ZipformerEars(
  model: Path,
  threads: Int,
) : Ears {
  override val name = "TalTechNLP/streaming-zipformer-large.et-en"
  private val recognizer = OnlineRecognizer(config(model, threads))

  override fun open(): Ear = ZipformerEar(recognizer)
}

private class ZipformerEar(
  private val recognizer: OnlineRecognizer,
) : Ear {
  private val stream = recognizer.createStream()
  private var rest = FloatArray(0)
  private var steps = 0
  private var begun = 0

  override fun hear(samples: FloatArray): List<Utterance> {
    val audio = rest + samples
    val whole = audio.size - audio.size % BLOCK
    rest = audio.copyOfRange(whole, audio.size)
    return feed(audio.copyOf(whole))
  }

  override fun end(): List<Segment> {
    val heard = feed(rest + FloatArray((TAIL * RATE).toInt()))
    stream.inputFinished()
    decode()
    val last = cut()
    stream.release()
    return (heard + last).flatMap { it.segments }
  }

  override fun close() {
    stream.release()
  }

  private fun feed(audio: FloatArray): List<Utterance> =
    audio.indices.step(BLOCK).mapNotNull { accept(audio.copyOfRange(it, minOf(it + BLOCK, audio.size))) }

  private fun accept(block: FloatArray): Utterance? {
    stream.acceptWaveform(block, RATE)
    decode()
    return if (recognizer.isEndpoint(stream)) cut() else null
  }

  private fun decode() {
    steps += generateSequence { if (recognizer.isReady(stream)) recognizer.decode(stream) else null }.count()
  }

  private fun cut(): Utterance {
    val result = recognizer.getResult(stream)
    val found = sentences(words(result.tokens, result.timestamps, begun * STEP))
    recognizer.reset(stream)
    begun = steps
    return Utterance(steps * STEP, found)
  }
}

internal fun words(
  tokens: Array<String>,
  times: FloatArray,
  offset: Double,
): List<Word> {
  val at = times.map { maxOf(0.0, offset + it - LAG) }
  val starts = tokens.indices.filter { it == 0 || tokens[it].startsWith(" ") || tokens[it].startsWith("▁") }
  return starts.zip(starts.drop(1) + tokens.size) { from, to ->
    val tail = at[to - 1] + TAIL
    Word(at[from], minOf(at.getOrElse(to) { tail }, tail), " " + tokens.copyOfRange(from, to).joinToString("").trimStart(' ', '▁'))
  }
}

internal fun sentences(words: List<Word>): List<Segment> {
  val cuts = words.indices.filter { it == words.lastIndex || words[it].word.takeLast(1) in STOPS }.map { it + 1 }
  return (listOf(0) + cuts)
    .zipWithNext { from, to -> words.subList(from, to) }
    .map { part -> Segment(part.first().start, part.last().end, part.joinToString("") { it.word }.trim(), part) }
}

private fun config(
  model: Path,
  threads: Int,
): OnlineRecognizerConfig {
  val transducer =
    OnlineTransducerModelConfig
      .builder()
      .setEncoder(model.resolve("encoder.int8.onnx").toString())
      .setDecoder(model.resolve("decoder.int8.onnx").toString())
      .setJoiner(model.resolve("joiner.int8.onnx").toString())
      .build()
  val weights =
    OnlineModelConfig
      .builder()
      .setTransducer(transducer)
      .setTokens(model.resolve("tokens.txt").toString())
      .setNumThreads(threads)
      .setDebug(false)
      .build()
  val endpoint =
    EndpointConfig
      .builder()
      .setRule1(rule(false, 2.4f, 0f))
      .setRule2(rule(true, 1.2f, 0f))
      .setRule3(rule(false, 0f, 3600f))
      .build()
  return OnlineRecognizerConfig
    .builder()
    .setOnlineModelConfig(weights)
    .setEndpointConfig(endpoint)
    .setEnableEndpoint(true)
    .setDecodingMethod("modified_beam_search")
    .build()
}

private fun rule(
  speech: Boolean,
  silence: Float,
  length: Float,
): EndpointRule =
  EndpointRule
    .builder()
    .setMustContainNonSilence(speech)
    .setMinTrailingSilence(silence)
    .setMinUtteranceLength(length)
    .build()
