package ee.tenman.portfolio.service.radio

import com.fasterxml.jackson.annotation.JsonProperty
import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiModel
import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

const val STATUS_FILE = "status.json"
const val TRANSCRIPT_FILE = "transcript.json"

@ConfigurationProperties(prefix = "radio")
data class RadioProperties(
  val recordings: Path = Path.of("/recordings"),
  val outlineModel: AiModel = AiModel.GEMINI_3_5_FLASH_LITE,
)

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
)

data class RecordingPart(
  val file: String,
  val offsetSeconds: Double,
  val sha256: String? = null,
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
)

data class Boundary(
  val boundary: String,
  val seconds: Double,
  val method: String,
  val review: Boolean,
)

fun readStatus(directory: Path): RecordingStatus =
  JsonMapperFactory.instance.readValue(Files.readString(directory.resolve(STATUS_FILE)), RecordingStatus::class.java)

fun readTranscript(directory: Path): Transcript =
  JsonMapperFactory.instance.readValue(Files.readString(directory.resolve(TRANSCRIPT_FILE)), Transcript::class.java)

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
)

data class TranscriptCheck(
  val checkedAt: String,
)
