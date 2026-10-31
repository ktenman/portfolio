package ee.tenman.portfolio.service.infrastructure

import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOException
import javax.imageio.ImageIO

class ImageProcessingServiceTest {
  private lateinit var service: ImageProcessingService

  @BeforeEach
  fun setUp() {
    service = ImageProcessingService()
  }

  @Test
  fun `should pad a wide image to a 256 pixel square`() {
    val result = service.resizeToMaxDimension(createTestImage(400, 100))
    expect(getImageDimensions(result)).toEqual(Pair(256, 256))
  }

  @Test
  fun `should keep the proportions of a wide image inside the square`() {
    val result = service.resizeToMaxDimension(png(400, 100, Color.BLUE, Color.WHITE))
    expect(listOf(pixel(result, 128, 97), pixel(result, 128, 90))).toEqual(listOf(Color.BLUE.rgb, Color.WHITE.rgb))
  }

  @Test
  fun `should pad a portrait image at the sides`() {
    val result = service.resizeToMaxDimension(png(100, 400, Color.BLUE, Color.WHITE))
    val pixels = listOf(pixel(result, 128, 128), pixel(result, 90, 128), pixel(result, 170, 128))
    expect(pixels).toEqual(listOf(Color.BLUE.rgb, Color.WHITE.rgb, Color.WHITE.rgb))
  }

  @Test
  fun `should fill the padding with the colour of the top left pixel`() {
    val result = service.resizeToMaxDimension(png(400, 100, Color.BLUE, Color.RED))
    expect(pixel(result, 0, 0)).toEqual(Color.RED.rgb)
  }

  @Test
  fun `should keep a transparent background transparent`() {
    val result = service.resizeToMaxDimension(png(400, 100, Color(0, 0, 0, 0)))
    expect(pixel(result, 0, 0) ushr 24).toEqual(0)
  }

  @ParameterizedTest
  @ValueSource(ints = [250, 256])
  fun `should leave an exact square from 250 to 256 pixels untouched`(side: Int) {
    val data = createTestImage(side, side)
    expect(service.resizeToMaxDimension(data).contentEquals(data)).toEqual(true)
  }

  @Test
  fun `should scale a small square up to 256 pixels`() {
    val result = service.resizeToMaxDimension(createTestImage(100, 100))
    expect(getImageDimensions(result)).toEqual(Pair(256, 256))
  }

  @Test
  fun `should scale a large square down to 256 pixels`() {
    val result = service.resizeToMaxDimension(createTestImage(300, 300))
    expect(getImageDimensions(result)).toEqual(Pair(256, 256))
  }

  @Test
  fun `should square a near-square image`() {
    val result = service.resizeToMaxDimension(createTestImage(255, 251))
    expect(getImageDimensions(result)).toEqual(Pair(256, 256))
  }

  @Test
  fun `should return original data when image cannot be read`() {
    val invalidData = byteArrayOf(0, 1, 2, 3)

    val result = service.resizeToMaxDimension(invalidData)

    expect(result).toEqual(invalidData)
  }

  @Test
  fun `should throw when a truncated image cannot be decoded`() {
    val data = createPhotoLikeImage(400, 100)
    expect { service.resizeToMaxDimension(data.copyOf(data.size / 2)) }.toThrow<IIOException>()
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

  private fun png(
    width: Int,
    height: Int,
    fill: Color,
    corner: Color = fill,
  ): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.color = fill
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()
    image.setRGB(0, 0, corner.rgb)
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(image, "PNG", outputStream)
    return outputStream.toByteArray()
  }

  private fun pixel(
    data: ByteArray,
    x: Int,
    y: Int,
  ): Int = ImageIO.read(ByteArrayInputStream(data)).getRGB(x, y)

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
