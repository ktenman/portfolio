package ee.tenman.radio

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.ZoneId
import java.util.HexFormat
import kotlin.math.max

const val RATE = 16000
const val KUKU = "https://router.euddn.net/189e8110a165b526b8b767d23e4ebb55/kuku/stream.m3u8?c=8000"
val TALLINN: ZoneId = ZoneId.of("Europe/Tallinn")
const val LOOKBACK = 3600.0
private val TOOLS = listOf("/usr/bin", "/usr/local/bin", "/opt/homebrew/bin").map { Path.of(it) }

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

data class Slot(
  val start: Long,
  val end: Long,
)

data class Part(
  val file: String,
  val streamStart: Double,
  val offsetSeconds: Double,
  val durationSeconds: Double? = null,
  val sha256: String? = null,
  val exitCode: Int? = null,
)

data class Status(
  val state: RecordingState,
  val source: String,
  val startedAt: Double,
  val endsAt: Double,
  val seconds: Double,
  val parts: List<Part> = emptyList(),
  val programmes: List<String>? = null,
  val slot: Slot? = null,
  val lostSeconds: Long? = null,
  val station: String? = null,
)

data class Capture(
  val name: String,
  val programmes: List<String>,
  val start: Long,
  val end: Long,
  val slot: Slot? = null,
)

data class Heartbeat(
  val seen: Double,
  val until: Double,
)

data class Word(
  val start: Double,
  val end: Double,
  val word: String,
)

data class Segment(
  val start: Double,
  val end: Double,
  val text: String,
  val words: List<Word>,
)

data class Chunk(
  val part: String,
  val from: Double,
  val to: Double,
  val final: Boolean,
  val segments: List<Segment>,
)

data class Line(
  val start: Double,
  val end: Double,
  val text: String,
)

data class Transcript(
  val model: String,
  val revision: String,
  val segments: List<Line>,
  val words: List<Word>,
)

data class SoundFrame(
  val at: Double,
  val speech: Double,
  val music: Double,
)

data class Sounds(
  val model: String,
  val revision: String,
  val frames: List<SoundFrame>,
)

class RecordingRefusedException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

val mapper: JsonMapper =
  JsonMapper
    .builder()
    .addModule(KotlinModule.Builder().build())
    .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()

inline fun <reified T> read(path: Path): T = mapper.readValue<T>(Files.readString(path))

fun save(
  path: Path,
  text: String,
) {
  val temporary = path.resolveSibling("${path.fileName}.tmp")
  Files.writeString(temporary, text)
  Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

fun write(
  path: Path,
  value: Any,
) {
  save(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n")
}

fun append(
  path: Path,
  value: Any,
) {
  Files.writeString(path, mapper.writeValueAsString(value) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
}

fun round3(value: Double): Double = BigDecimal(value).setScale(3, RoundingMode.HALF_EVEN).toDouble()

fun revision(model: Path): String = Files.readString(model.resolve("revision")).trim()

fun tool(
  name: String,
  directories: List<Path> = TOOLS,
): String = directories.map { it.resolve(name) }.firstOrNull(Files::isExecutable)?.toString() ?: error("$name is not in $directories")

fun now(): Double = System.currentTimeMillis() / 1000.0

fun pcm(
  directory: Path,
  part: Part,
): Path = directory.resolve("${part.file.substringBeforeLast('.')}.pcm")

fun samples(pcm: Path): Long = if (Files.exists(pcm)) Files.size(pcm) / 2 else 0

fun seconds(pcm: Path): Double = samples(pcm) / RATE.toDouble()

fun floats(
  pcm: Path,
  first: Long,
  count: Int,
): FloatArray {
  val bytes =
    Files.newInputStream(pcm).use {
      it.skipNBytes(2 * first)
      it.readNBytes(2 * count)
    }
  val audio = ByteBuffer.wrap(bytes.copyOf(2 * count)).order(ByteOrder.LITTLE_ENDIAN)
  return FloatArray(count) { audio.getShort(2 * it) / 32768f }
}

fun overlap(
  directory: Path,
  parts: List<Part>,
  index: Int,
): Double {
  val end = parts.take(index).maxOfOrNull { it.offsetSeconds + seconds(pcm(directory, it)) } ?: return 0.0
  return max(0.0, end - parts[index].offsetSeconds)
}

fun sha256(path: Path): String {
  val digest = MessageDigest.getInstance("SHA-256")
  DigestInputStream(Files.newInputStream(path), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
  return HexFormat.of().formatHex(digest.digest())
}
