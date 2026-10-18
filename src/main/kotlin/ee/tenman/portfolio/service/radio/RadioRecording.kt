package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiModel
import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path

const val STATUS_FILE = "status.json"
const val TRANSCRIPT_FILE = "transcript.json"

@ConfigurationProperties(prefix = "radio")
data class RadioProperties(
  val recordings: Path = Path.of("/recordings"),
  val outlineModel: AiModel = AiModel.GEMINI_3_5_FLASH_LITE,
)

data class RecordingStatus(
  val state: String,
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

fun rounded(seconds: Double): Double = BigDecimal(seconds).setScale(2, RoundingMode.HALF_EVEN).toDouble()
