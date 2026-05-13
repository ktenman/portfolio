package ee.tenman.portfolio.service.infrastructure

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageProcessingServiceTest {
  private lateinit var service: ImageProcessingService

  @BeforeEach
  fun setUp() {
    service = ImageProcessingService()
  }

  @Test
  fun `should not resize image within bounds`() {
    val imageData = createTestImage(255, 251)

    val result = service.resizeToMaxDimension(imageData)
    val dimensions = getImageDimensions(result)

    expect(dimensions.first).toEqual(255)
    expect(dimensions.second).toEqual(251)
  }

  @Test
  fun `should scale down large image based on max dimension`() {
    val imageData = createTestImage(512, 256)

    val result = service.resizeToMaxDimension(imageData)
    val dimensions = getImageDimensions(result)

    expect(dimensions.first).toBeLessThanOrEqualTo(256)
    expect(dimensions.second).toBeLessThanOrEqualTo(256)
  }

  @Test
  fun `should scale up small image based on min dimension`() {
    val imageData = createTestImage(100, 100)

    val result = service.resizeToMaxDimension(imageData)
    val dimensions = getImageDimensions(result)

    expect(dimensions.first).toBeGreaterThanOrEqualTo(250)
    expect(dimensions.second).toBeGreaterThanOrEqualTo(250)
  }

  @Test
  fun `should maintain aspect ratio when scaling down`() {
    val imageData = createTestImage(520, 500)

    val result = service.resizeToMaxDimension(imageData)
    val dimensions = getImageDimensions(result)

    expect(dimensions.first).toEqual(256)
    expect(dimensions.second).toEqual(246)
  }

  @Test
  fun `should maintain aspect ratio when scaling up`() {
    val imageData = createTestImage(100, 80)

    val result = service.resizeToMaxDimension(imageData)
    val dimensions = getImageDimensions(result)

    expect(dimensions.first).toBeGreaterThanOrEqualTo(250)
  }

  @Test
  fun `should return original data when image cannot be read`() {
    val invalidData = byteArrayOf(0, 1, 2, 3)

    val result = service.resizeToMaxDimension(invalidData)

    expect(result).toEqual(invalidData)
  }

  @Test
  fun `should not resize plate image already within plate bounds`() {
    val imageData = createTestImage(800, 600)

    val result = service.resizeForPlateDetection(imageData)

    expect(result).toEqual(imageData)
  }

  @Test
  fun `should scale down oversized plate image to 800 max dimension`() {
    val imageData = createTestImage(4000, 3000)

    val result = service.resizeForPlateDetection(imageData)
    val (width, height) = getImageDimensions(result)

    expect(width).toBeLessThanOrEqualTo(800)
    expect(height).toBeLessThanOrEqualTo(800)
    expect(width).toEqual(800)
  }

  @Test
  fun `should preserve aspect ratio when downscaling plate image`() {
    val imageData = createTestImage(4000, 2000)

    val result = service.resizeForPlateDetection(imageData)
    val (width, height) = getImageDimensions(result)

    expect(width).toEqual(800)
    expect(height).toEqual(400)
  }

  @Test
  fun `should not upscale small plate images`() {
    val imageData = createTestImage(400, 300)

    val result = service.resizeForPlateDetection(imageData)

    expect(result).toEqual(imageData)
  }

  @Test
  fun `should produce smaller byte payload after downscaling a large photo`() {
    val imageData = createPhotoLikeImage(4000, 3000)

    val result = service.resizeForPlateDetection(imageData)

    expect(result.size).toBeLessThan(imageData.size)
  }

  @Test
  fun `should return original data when plate image cannot be read`() {
    val invalidData = byteArrayOf(9, 8, 7, 6)

    val result = service.resizeForPlateDetection(invalidData)

    expect(result).toEqual(invalidData)
  }

  private fun createTestImage(
    width: Int,
    height: Int,
  ): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(image, "PNG", outputStream)
    return outputStream.toByteArray()
  }

  private fun createPhotoLikeImage(
    width: Int,
    height: Int,
  ): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val r = (x * 255 / width) and 0xFF
        val g = (y * 255 / height) and 0xFF
        val b = ((x + y) * 255 / (width + height)) and 0xFF
        image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
      }
    }
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(image, "PNG", outputStream)
    return outputStream.toByteArray()
  }

  private fun getImageDimensions(imageData: ByteArray): Pair<Int, Int> {
    val image = ImageIO.read(ByteArrayInputStream(imageData))
    return Pair(image.width, image.height)
  }
}
