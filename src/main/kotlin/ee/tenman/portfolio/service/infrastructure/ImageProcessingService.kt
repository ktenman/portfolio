package ee.tenman.portfolio.service.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.roundToInt

@Service
class ImageProcessingService {
  private val log = LoggerFactory.getLogger(javaClass)

  fun resizeToMaxDimension(imageData: ByteArray): ByteArray {
    val source = ImageIO.read(ByteArrayInputStream(imageData)) ?: return imageData
    if (source.width == source.height && source.width in MIN_DIMENSION..MAX_DIMENSION) {
      log.debug("Image ${source.width}x${source.height} is already square, no padding needed")
      return imageData
    }
    log.debug("Padding image ${source.width}x${source.height} to a ${MAX_DIMENSION}x$MAX_DIMENSION square")
    return padToSquare(source)
  }

  fun resizeForPlateDetection(imageData: ByteArray): ByteArray {
    val originalImage = ImageIO.read(ByteArrayInputStream(imageData)) ?: return imageData
    val originalWidth = originalImage.width
    val originalHeight = originalImage.height
    val maxSide = maxOf(originalWidth, originalHeight)
    if (maxSide <= PLATE_MAX_DIMENSION) {
      log.debug("Plate image ${originalWidth}x$originalHeight within bounds, no resize needed")
      return imageData
    }
    val scale = PLATE_MAX_DIMENSION.toDouble() / maxSide
    val newWidth = (originalWidth * scale).toInt()
    val newHeight = (originalHeight * scale).toInt()
    log.debug("Resizing plate image from ${originalWidth}x$originalHeight to ${newWidth}x$newHeight")
    val resized = renderResized(originalImage, newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    return encodeJpeg(resized, PLATE_JPEG_QUALITY)
  }

  private fun renderResized(
    source: BufferedImage,
    newWidth: Int,
    newHeight: Int,
    imageType: Int,
  ): BufferedImage {
    val resized = BufferedImage(newWidth, newHeight, imageType)
    val graphics = resized.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.drawImage(source, 0, 0, newWidth, newHeight, null)
    graphics.dispose()
    return resized
  }

  private fun encodeJpeg(
    image: BufferedImage,
    quality: Float,
  ): ByteArray {
    val outputStream = ByteArrayOutputStream()
    val writer = ImageIO.getImageWritersByFormatName("jpg").next()
    MemoryCacheImageOutputStream(outputStream).use { ios ->
      writer.output = ios
      val param =
        writer.defaultWriteParam.apply {
          compressionMode = ImageWriteParam.MODE_EXPLICIT
          compressionQuality = quality
        }
      try {
        writer.write(null, IIOImage(image, null, null), param)
      } finally {
        writer.dispose()
      }
    }
    return outputStream.toByteArray()
  }

  private fun padToSquare(source: BufferedImage): ByteArray {
    val scale = MAX_DIMENSION.toDouble() / maxOf(source.width, source.height)
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    val resized = renderResized(source, width, height, BufferedImage.TYPE_INT_ARGB)
    val canvas = BufferedImage(MAX_DIMENSION, MAX_DIMENSION, BufferedImage.TYPE_INT_ARGB)
    val graphics = canvas.createGraphics()
    graphics.color = Color(source.getRGB(0, 0), true)
    graphics.fillRect(0, 0, MAX_DIMENSION, MAX_DIMENSION)
    graphics.drawImage(resized, (MAX_DIMENSION - width) / 2, (MAX_DIMENSION - height) / 2, null)
    graphics.dispose()
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(canvas, "PNG", outputStream)
    return outputStream.toByteArray()
  }

  companion object {
    const val MAX_DIMENSION = 256
    const val MIN_DIMENSION = 250
    const val PLATE_MAX_DIMENSION = 800
    const val PLATE_JPEG_QUALITY = 0.75f
  }
}
