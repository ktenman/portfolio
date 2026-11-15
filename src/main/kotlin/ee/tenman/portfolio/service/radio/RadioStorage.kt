package ee.tenman.portfolio.service.radio

import io.minio.GetObjectArgs
import io.minio.MinioClient
import org.springframework.core.io.AbstractResource
import org.springframework.core.io.Resource
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max

const val UPLOAD = "upload.json"
val AUDIO = Regex("episode\\.m4a|raw(-part-\\d{3})?\\.mp3")

data class StoredFile(
  val size: Long,
  val modified: Long,
)

data class Upload(
  val bucket: String,
  val files: Map<String, StoredFile>,
)

fun stored(path: Path): StoredFile = StoredFile(Files.size(path), Files.getLastModifiedTime(path).toMillis())

class StoredResource(
  private val client: MinioClient,
  private val bucket: String,
  private val key: String,
  private val file: StoredFile,
) : AbstractResource() {
  override fun getDescription(): String = "S3 object $bucket/$key"

  override fun getFilename(): String = key.substringAfterLast('/')

  override fun exists(): Boolean = true

  override fun contentLength(): Long = file.size

  override fun lastModified(): Long = file.modified

  override fun getInputStream(): InputStream = RangeStream(::read)

  private fun read(offset: Long): InputStream =
    client.getObject(
      GetObjectArgs
        .builder()
        .bucket(bucket)
        .`object`(key)
        .offset(offset)
        .build(),
    )
}

class RangeStream(
  private val open: (Long) -> InputStream,
) : InputStream() {
  private var offset = 0L
  private var stream: InputStream? = null

  override fun skip(n: Long): Long {
    if (stream != null) return opened().skip(n)
    offset += n
    return n
  }

  override fun read(): Int = opened().read()

  override fun read(
    b: ByteArray,
    off: Int,
    len: Int,
  ): Int = opened().read(b, off, len)

  override fun close() {
    stream?.close()
  }

  private fun opened(): InputStream = stream ?: open(offset).also { stream = it }
}

const val SHOW_FILE = "show.mp3"

data class ShowPart(
  val resource: Resource,
  val inpoint: Double,
)

data class RadioShow(
  val file: String,
  val parts: List<ShowPart>,
)

fun inpoints(parts: List<RecordingPart>): List<Double> =
  parts
    .runningFold(0.0) { reached, part -> max(reached, part.offsetSeconds + (part.durationSeconds ?: 0.0)) }
    .zip(parts) { reached, part -> max(0.0, reached - part.offsetSeconds) }
