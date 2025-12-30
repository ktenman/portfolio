package ee.tenman.portfolio.service.logo

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Service
class LogoValidationService {
  private val log = LoggerFactory.getLogger(javaClass)

  fun isValidLogo(imageData: ByteArray): Boolean {
    if (imageData.isEmpty()) return false
    val dimensions = getImageDimensions(imageData) ?: return logAndReturnFalse("Could not determine image dimensions")
    return validateDimensions(dimensions)
  }

  private fun validateDimensions(dimensions: ImageDimensions): Boolean {
    val isSquare = isSquareAspectRatio(dimensions)
    val hasMinimumSize = dimensions.width >= MIN_DIMENSION && dimensions.height >= MIN_DIMENSION
    return when {
      !isSquare -> logAndReturnFalse("Image is not square: ${dimensions.width}x${dimensions.height}")
      !hasMinimumSize -> logAndReturnFalse("Image is too small: ${dimensions.width}x${dimensions.height}")
      else -> true
    }
  }

  private fun logAndReturnFalse(message: String): Boolean {
    log.debug(message)
    return false
  }

  private fun isSquareAspectRatio(dimensions: ImageDimensions): Boolean {
    val ratio = dimensions.width.toDouble() / dimensions.height.toDouble()
    return ratio in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO
  }

  private fun getImageDimensions(data: ByteArray): ImageDimensions? =
    when {
      isPng(data) -> getPngDimensions(data)
      isJpeg(data) -> getJpegDimensions(data)
      isWebP(data) -> getWebPDimensions(data)
      else -> null
    }

  private fun isPng(data: ByteArray): Boolean =
    data.size >= 8 &&
      data[0] == 0x89.toByte() &&
      data[1] == 0x50.toByte() &&
      data[2] == 0x4E.toByte() &&
      data[3] == 0x47.toByte()

  private fun isJpeg(data: ByteArray): Boolean =
    data.size >= 3 &&
      data[0] == 0xFF.toByte() &&
      data[1] == 0xD8.toByte() &&
      data[2] == 0xFF.toByte()

  private fun isWebP(data: ByteArray): Boolean =
    data.size >= 12 &&
      data[0] == 0x52.toByte() &&
      data[1] == 0x49.toByte() &&
      data[2] == 0x46.toByte() &&
      data[3] == 0x46.toByte() &&
      data[8] == 0x57.toByte() &&
      data[9] == 0x45.toByte() &&
      data[10] == 0x42.toByte() &&
      data[11] == 0x50.toByte()

  private fun getPngDimensions(data: ByteArray): ImageDimensions? {
    if (data.size < 24) return null
    val buffer = ByteBuffer.wrap(data, 16, 8).order(ByteOrder.BIG_ENDIAN)
    val width = buffer.int
    val height = buffer.int
    return ImageDimensions(width, height)
  }

  private fun getJpegDimensions(data: ByteArray): ImageDimensions? {
    var offset = 2
    while (offset < data.size - 8) {
      if (data[offset] != 0xFF.toByte()) break
      val marker = data[offset + 1].toInt() and 0xFF
      if (marker == 0xC0 || marker == 0xC2) {
        val height = ((data[offset + 5].toInt() and 0xFF) shl 8) or (data[offset + 6].toInt() and 0xFF)
        val width = ((data[offset + 7].toInt() and 0xFF) shl 8) or (data[offset + 8].toInt() and 0xFF)
        return ImageDimensions(width, height)
      }
      val segmentLength = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
      offset += 2 + segmentLength
    }
    return null
  }

  private fun getWebPDimensions(data: ByteArray): ImageDimensions? {
    if (data.size <= 26) return null
    if (data[12] != 0x56.toByte() || data[13] != 0x50.toByte() || data[14] != 0x38.toByte()) return null
    if (data[15] != 0x20.toByte()) return null
    val width = ((data[26].toInt() and 0xFF) or ((data[27].toInt() and 0x3F) shl 8))
    val height = ((data[28].toInt() and 0xFF) or ((data[29].toInt() and 0x3F) shl 8))
    return ImageDimensions(width, height)
  }

  companion object {
    private const val MIN_DIMENSION = 32
    private const val MIN_ASPECT_RATIO = 0.9
    private const val MAX_ASPECT_RATIO = 1.1
  }
}
