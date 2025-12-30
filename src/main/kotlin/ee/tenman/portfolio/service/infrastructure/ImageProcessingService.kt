package ee.tenman.portfolio.service.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Service
class ImageProcessingService {
  private val log = LoggerFactory.getLogger(javaClass)

  fun resizeToMaxDimension(imageData: ByteArray): ByteArray {
    val inputStream = ByteArrayInputStream(imageData)
    val originalImage = ImageIO.read(inputStream) ?: return imageData
    val originalWidth = originalImage.width
    val originalHeight = originalImage.height
    val scale = calculateScale(originalWidth, originalHeight)
    if (scale == 1.0) {
      log.debug("Image ${originalWidth}x$originalHeight within bounds, no resize needed")
      return imageData
    }
    val newWidth = (originalWidth * scale).toInt()
    val newHeight = (originalHeight * scale).toInt()
    log.debug("Resizing image from ${originalWidth}x$originalHeight to ${newWidth}x$newHeight")
    return resizeImage(originalImage, newWidth, newHeight)
  }

  private fun calculateScale(
    width: Int,
    height: Int,
  ): Double {
    val maxSide = maxOf(width, height)
    val minSide = minOf(width, height)
    if (maxSide > MAX_DIMENSION) return MAX_DIMENSION.toDouble() / maxSide
    if (minSide < MIN_DIMENSION) return MIN_DIMENSION.toDouble() / minSide
    return 1.0
  }

  private fun resizeImage(
    originalImage: BufferedImage,
    newWidth: Int,
    newHeight: Int,
  ): ByteArray {
    val resizedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = resizedImage.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.drawImage(originalImage, 0, 0, newWidth, newHeight, null)
    graphics.dispose()
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(resizedImage, "PNG", outputStream)
    return outputStream.toByteArray()
  }

  companion object {
    const val MAX_DIMENSION = 256
    const val MIN_DIMENSION = 250
  }
}
