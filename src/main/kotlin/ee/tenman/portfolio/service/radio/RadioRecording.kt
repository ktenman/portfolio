package ee.tenman.portfolio.service.radio

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.Station
import org.springframework.boot.context.properties.ConfigurationProperties
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val STATUS_FILE = "status.json"
const val TRANSCRIPT_FILE = "transcript.json"
const val DELETED_FILE = "deleted.json"
const val EDIT_LIST_FILE = "edit-list.json"
const val REVIEW_FILE = "review.json"
val NAME = Regex("[0-9a-z][0-9a-z-]*")
private val COMMIT = Regex("[0-9a-f]{40}")
private val MODELS = mapOf("TalTechNLP/streaming-zipformer-large.et-en" to "Zipformer", "ced-tiny int8" to "CED-tiny")

@ConfigurationProperties(prefix = "radio")
data class RadioProperties(
  val recordings: Path = Path.of("/recordings"),
  val outlineModel: AiModel = AiModel.GEMINI_4_ARGON,
  val live: String = "https://router.euddn.net/189e8110a165b526b8b767d23e4ebb55/kuku/stream.m3u8?c=8000",
  val aripaevLive: String = "https://www.aripaev.ee/raadio/stream.mp3",
  val bucket: String = "radio",
  val localDays: Long = 7,
) {
  val jingles: Path
    get() = recordings.resolve("jingles")
}

enum class RecordingState {
  @JsonProperty("recording")
  RECORDING,

  @JsonProperty("captured")
  CAPTURED,

  @JsonProperty("partial")
  PARTIAL,

  @JsonProperty("failed")
  FAILED,
}

data class RecordingStatus(
  val state: RecordingState,
  val startedAt: Double = 0.0,
  val seconds: Double = 0.0,
  val parts: List<RecordingPart> = emptyList(),
  val programmes: List<String> = listOf(PROGRAMME),
  val lostSeconds: Long = 0,
  val slot: Slot? = null,
  val station: Station = Station.KUKU,
)

fun RecordingStatus.title(): String = programmes.joinToString(", ")

private val UNSAFE = Regex("""[/\\:*?"<>|]""")

fun downloadName(
  name: String,
  status: RecordingStatus,
  file: String,
  zone: ZoneId,
): String {
  val day =
    runCatching { LocalDate.parse(name.take(10)) }
      .getOrElse { Instant.ofEpochMilli((status.startedAt * 1000).toLong()).atZone(zone).toLocalDate() }
  val stem = "${status.title().replace(UNSAFE, "")} $day"
  val extension = file.substringAfterLast('.')
  val parts = status.parts.sortedBy { it.offsetSeconds }
  val part = parts.indexOfFirst { it.file == file }
  return when {
    part < 0 -> "$stem.$extension"
    parts.size == 1 -> "$stem originaal.$extension"
    else -> "$stem originaal osa ${part + 1}.$extension"
  }
}

data class RecordingPart(
  val file: String,
  val offsetSeconds: Double,
  val sha256: String? = null,
  val durationSeconds: Double? = null,
)

data class AudioFormat(
  @JsonAlias("codec_name") val codec: String,
  @JsonAlias("bit_rate") val bitRate: Long,
)

data class Probe(
  val streams: List<AudioFormat> = emptyList(),
)

data class Transcript(
  val segments: List<TranscriptSegment>,
  val words: List<TranscriptWord>,
)

data class TranscriptSegment(
  val start: Double,
  val end: Double,
  val text: String,
)

data class TranscriptWord(
  val start: Double,
  val end: Double,
  val word: String,
)

data class TranscriptLine(
  val start: Double,
  val end: Double,
  val text: String,
  val words: List<TranscriptWord>,
  val split: Cut? = null,
  val music: Boolean = false,
)

data class Boundary(
  val boundary: String,
  val seconds: Double,
  val method: String,
  val review: Boolean,
  val jingle: JingleMatch? = null,
  val programme: String? = null,
)

data class JingleMatch(
  val start: Double,
  val r: Double,
  val saved: String? = null,
)

fun directories(root: Path): List<Path> {
  if (!Files.isDirectory(root)) return emptyList()
  return Files
    .list(root)
    .use { it.toList() }
    .filter { NAME.matches(it.fileName.toString()) && Files.exists(it.resolve(STATUS_FILE)) }
    .sortedDescending()
}

fun readStatus(directory: Path): RecordingStatus =
  JsonMapperFactory.instance.readValue(Files.readString(directory.resolve(STATUS_FILE)), RecordingStatus::class.java)

fun readTranscript(directory: Path): Transcript =
  JsonMapperFactory.instance.readValue(Files.readString(directory.resolve(TRANSCRIPT_FILE)), Transcript::class.java)

fun readModel(path: Path): ModelVersion? {
  if (Files.notExists(path)) return null
  val fields =
    JsonMapperFactory.instance.createParser(path).use { parser ->
      parser.nextToken()
      List(2) { parser.nextName() to parser.nextStringValue() }.toMap()
    }
  return ModelVersion(fields["model"] ?: return null, fields["revision"] ?: return null)
}

inline fun <reified T> read(path: Path): T? =
  path.takeIf(Files::exists)?.let { JsonMapperFactory.instance.readValue<T>(Files.readString(it)) }

fun save(
  path: Path,
  value: Any,
) {
  val temporary = path.resolveSibling("${path.fileName}.tmp")
  Files.writeString(temporary, JsonMapperFactory.instance.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n")
  Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

fun rounded(seconds: Double): Double = BigDecimal(seconds).setScale(2, RoundingMode.HALF_EVEN).toDouble()

data class ModelVersion(
  val model: String,
  val revision: String,
) {
  val label: String
    get() = "${MODELS[model] ?: model} · ${if (COMMIT.matches(revision)) revision.take(7) else revision}"
}

data class RecordingSummary(
  val name: String,
  val programme: String,
  val status: RecordingStatus,
  val sizeBytes: Long,
  val transcribed: Boolean,
  val speech: ModelVersion?,
  val sounds: ModelVersion?,
  val editList: EditList?,
  val failure: EpisodeFailure?,
  val episodeBytes: Long?,
  val cuts: List<Cut>,
  val timeline: List<TimelineEntry>,
  val audible: List<KeptSpan>,
  val format: AudioFormat?,
  val programmeCuts: List<ProgrammeCuts>,
  val render: RenderProgress? = null,
  val renderFailure: EpisodeFailure? = null,
) {
  val drops: Int
    get() = (status.parts.size - 1).coerceAtLeast(0)

  val span: Slot
    get() {
      val start = status.startedAt.toLong()
      val end = (status.startedAt + status.seconds).toLong()
      val slot = status.slot ?: return Slot(start, end)
      return Slot(maxOf(start, slot.start), minOf(end, slot.end))
    }

  val topics: List<TimelineEntry>
    get() = heard(timeline, audible)

  val transcriptState: TranscriptState
    get() =
      when {
        transcribed -> TranscriptState.TRANSCRIBED
        status.state == RecordingState.RECORDING -> TranscriptState.RECORDING
        else -> TranscriptState.NONE
      }

  val cutState: CutState
    get() =
      when {
        failure != null -> CutState.FAILED
        editList == null -> CutState.NONE
        editList.reviewed -> CutState.REVIEWED
        else -> CutState.SUGGESTED
      }
}

enum class TranscriptState {
  NONE,
  RECORDING,
  TRANSCRIBED,
}

enum class CutState {
  NONE,
  FAILED,
  SUGGESTED,
  REVIEWED,
}

enum class ProgrammeCutState {
  FOUND,
  FINDING,
  QUEUED,
  WAITING,
  FAILED,
  NONE,
}

data class ProgrammeCuts(
  val name: String,
  val state: ProgrammeCutState,
  val progress: CutProgress? = null,
  val transcript: TranscriptProgress? = null,
  val after: String? = null,
)

data class CutChoice(
  val programme: String,
)

fun EditList?.decided(pending: List<KeptSpan>?): EditList? = pending?.let { this?.copy(keep = it) } ?: this

fun EditList?.found(): Set<String> =
  this
  ?.boundaries
  ?.map { it.programme ?: PROGRAMME }
  ?.toSet()
  .orEmpty()

data class RadioPositionDto(
  val seconds: Double,
)
