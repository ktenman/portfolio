package ee.tenman.portfolio.googlevision

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

object FileToBase64 {
  private val log = LoggerFactory.getLogger(javaClass)
  private val BASE64_ENCODER = Base64.getEncoder()
  private val BASE64_DECODER = Base64.getDecoder()

  fun encodeToBase64(filePath: String): String =
    try {
      encodeToBase64(Files.readAllBytes(Paths.get(filePath)))
    } catch (e: Exception) {
      log.error("Error reading file: $filePath", e)
      ""
    }

  fun encodeToBase64(fileContent: ByteArray): String = BASE64_ENCODER.encodeToString(fileContent)

  fun decode(base64EncodedKey: String): ByteArray = BASE64_DECODER.decode(base64EncodedKey)
}
