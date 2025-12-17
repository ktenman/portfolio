package ee.tenman.portfolio.service

import net.coobird.thumbnailator.Thumbnails
import org.imgscalr.Scalr
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

object ImageOptimizer {
    const val DEFAULT_MAX_WIDTH = 900
    const val DEFAULT_JPEG_QUALITY = 0.75f
    private val log = LoggerFactory.getLogger(javaClass)

    fun optimize(
      inputBytes: ByteArray,
      strategy: OptimizationStrategy,
      maxWidth: Int = DEFAULT_MAX_WIDTH,
      quality: Float = DEFAULT_JPEG_QUALITY,
    ): ByteArray =
        when (strategy) {
            OptimizationStrategy.NONE -> inputBytes
            OptimizationStrategy.THUMBNAILATOR -> optimizeWithThumbnailator(inputBytes, maxWidth, quality)
            OptimizationStrategy.IMGSCALR -> optimizeWithImgscalr(inputBytes, maxWidth, quality)
        }

    private fun optimizeWithThumbnailator(
      inputBytes: ByteArray,
      maxWidth: Int,
      quality: Float,
    ): ByteArray =
        runCatching {
            if (inputBytes.isEmpty()) return inputBytes
            val startTime = System.currentTimeMillis()
            val output = ByteArrayOutputStream()
            Thumbnails
                .of(ByteArrayInputStream(inputBytes))
                .size(maxWidth, maxWidth)
                .outputQuality(quality.toDouble())
                .outputFormat("jpg")
                .toOutputStream(output)
            val result = output.toByteArray()
            val elapsed = System.currentTimeMillis() - startTime
            log.info(
                "Thumbnailator({}px,{}%): {}KB -> {}KB in {}ms",
                maxWidth,
                (quality * 100).toInt(),
                inputBytes.size / 1024,
                result.size / 1024,
                elapsed,
            )
            result
        }.getOrElse { e ->
            log.warn("Thumbnailator optimization failed: {}", e.message)
            inputBytes
        }

    private fun optimizeWithImgscalr(
      inputBytes: ByteArray,
      maxWidth: Int,
      quality: Float,
    ): ByteArray =
        runCatching {
            if (inputBytes.isEmpty()) return inputBytes
            val startTime = System.currentTimeMillis()
            val original = ImageIO.read(ByteArrayInputStream(inputBytes)) ?: return inputBytes
            if (original.width <= maxWidth && original.height <= maxWidth) {
                return compressToJpeg(original, inputBytes, startTime, quality, "imgscalr (no resize)")
            }
            val resized = Scalr.resize(original, Scalr.Method.BALANCED, maxWidth)
            compressToJpeg(resized, inputBytes, startTime, quality, "imgscalr")
        }.getOrElse { e ->
            log.warn("imgscalr optimization failed: {}", e.message)
            inputBytes
        }

    private fun compressToJpeg(
      image: BufferedImage,
      originalBytes: ByteArray,
      startTime: Long,
      quality: Float,
      source: String,
    ): ByteArray {
        val rgbImage =
            if (image.type != BufferedImage.TYPE_INT_RGB) {
                BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also { rgb ->
                    rgb.createGraphics().apply {
                        drawImage(image, 0, 0, null)
                        dispose()
                    }
                }
            } else {
                image
            }
        val output = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val writeParam =
            writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
        ImageIO.createImageOutputStream(output).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(rgbImage, null, null), writeParam)
        }
        writer.dispose()
        val result = output.toByteArray()
        val elapsed = System.currentTimeMillis() - startTime
        log.info(
            "{}: {}KB -> {}KB in {}ms",
            source,
            originalBytes.size / 1024,
            result.size / 1024,
            elapsed,
        )
        return result
    }
}
