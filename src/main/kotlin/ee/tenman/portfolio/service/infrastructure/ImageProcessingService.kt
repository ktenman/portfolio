package ee.tenman.portfolio.service.infrastructure

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

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
    val resized = renderResized(originalImage, newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(resized, "PNG", outputStream)
    return outputStream.toByteArray()
  }

  companion object {
    const val MAX_DIMENSION = 256
    const val MIN_DIMENSION = 250
    const val PLATE_MAX_DIMENSION = 800
    const val PLATE_JPEG_QUALITY = 0.75f
  }
}
