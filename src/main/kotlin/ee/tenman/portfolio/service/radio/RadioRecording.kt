package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiModel
import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Files
import java.nio.file.Path

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

fun readStatus(directory: Path): RecordingStatus =
  JsonMapperFactory.instance.readValue(Files.readString(directory.resolve("status.json")), RecordingStatus::class.java)

fun readTranscript(directory: Path): Transcript =
  JsonMapperFactory.instance.readValue(Files.readString(directory.resolve("transcript.json")), Transcript::class.java)
