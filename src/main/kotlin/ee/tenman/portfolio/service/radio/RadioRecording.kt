package ee.tenman.portfolio.service.radio

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiModel
import org.springframework.boot.context.properties.ConfigurationProperties
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

const val STATUS_FILE = "status.json"
const val TRANSCRIPT_FILE = "transcript.json"
const val CUT_REQUEST = "cuts-requested.json"

@ConfigurationProperties(prefix = "radio")
data class RadioProperties(
  val recordings: Path = Path.of("/recordings"),
  val outlineModel: AiModel = AiModel.GEMINI_3_5_FLASH_LITE,
  val live: String = "https://router.euddn.net/189e8110a165b526b8b767d23e4ebb55/kuku/stream.m3u8?c=8000",
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
)

fun RecordingStatus.title(): String = programmes.joinToString(", ")

data class RecordingPart(
  val file: String,
  val offsetSeconds: Double,
  val sha256: String? = null,
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

fun readStatus(directory: Path): RecordingStatus =
  JsonMapperFactory.instance.readValue(Files.readString(directory.resolve(STATUS_FILE)), RecordingStatus::class.java)

fun readTranscript(directory: Path): Transcript =
  JsonMapperFactory.instance.readValue(Files.readString(directory.resolve(TRANSCRIPT_FILE)), Transcript::class.java)

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

data class RecordingSummary(
  val name: String,
  val programme: String,
  val status: RecordingStatus,
  val sizeBytes: Long,
  val transcribed: Boolean,
  val transcriptCheckedAt: String?,
  val editList: EditList?,
  val failure: EpisodeFailure?,
  val episodeBytes: Long?,
  val cuts: List<Cut>,
  val timeline: List<TimelineEntry>,
  val audible: List<KeptSpan>,
  val format: AudioFormat?,
  val programmeCuts: List<ProgrammeCuts>,
) {
  val drops: Int
    get() = (status.parts.size - 1).coerceAtLeast(0)

  val transcriptState: TranscriptState
    get() =
      when {
        transcribed && transcriptCheckedAt != null -> TranscriptState.CHECKED
        transcribed -> TranscriptState.UNCHECKED
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
  UNCHECKED,
  CHECKED,
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
  NONE,
}

data class ProgrammeCuts(
  val name: String,
  val state: ProgrammeCutState,
)

data class CutRequest(
  val programmes: List<String>,
)

data class CutChoice(
  val programme: String,
)

fun wanted(
  status: RecordingStatus,
  requested: List<String>,
): List<String> = status.programmes.filter { it == PROGRAMME || it in requested }

fun EditList?.found(): Set<String> =
  this
  ?.boundaries
  ?.map { it.programme ?: PROGRAMME }
  ?.toSet()
  .orEmpty()

data class TranscriptCheck(
  val checkedAt: String,
)
