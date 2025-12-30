package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LogoValidationServiceTest {
  private lateinit var service: LogoValidationService

  @BeforeEach
  fun setUp() {
    service = LogoValidationService()
  }

  @Test
  fun `should return false for empty data`() {
    val result = service.isValidLogo(byteArrayOf())

    expect(result).toEqual(false)
  }

  @Test
  fun `should return false for data too small to be an image`() {
    val result = service.isValidLogo(byteArrayOf(1, 2, 3))

    expect(result).toEqual(false)
  }

  @Test
  fun `should return false for non-image data`() {
    val textData = "Hello, World!".toByteArray()

    val result = service.isValidLogo(textData)

    expect(result).toEqual(false)
  }

  @Test
  fun `should validate square PNG image`() {
    val pngData = createSquarePngHeader(100, 100)

    val result = service.isValidLogo(pngData)

    expect(result).toEqual(true)
  }

  @Test
  fun `should reject non-square PNG image`() {
    val pngData = createSquarePngHeader(200, 100)

    val result = service.isValidLogo(pngData)

    expect(result).toEqual(false)
  }

  @Test
  fun `should accept slightly non-square PNG within tolerance`() {
    val pngData = createSquarePngHeader(100, 105)

    val result = service.isValidLogo(pngData)

    expect(result).toEqual(true)
  }

  @Test
  fun `should reject PNG image too small`() {
    val pngData = createSquarePngHeader(20, 20)

    val result = service.isValidLogo(pngData)

    expect(result).toEqual(false)
  }

  @Test
  fun `should validate square JPEG image`() {
    val jpegData = createSquareJpegHeader(100, 100)

    val result = service.isValidLogo(jpegData)

    expect(result).toEqual(true)
  }

  @Test
  fun `should reject non-square JPEG image`() {
    val jpegData = createSquareJpegHeader(200, 100)

    val result = service.isValidLogo(jpegData)

    expect(result).toEqual(false)
  }

  private fun createSquarePngHeader(
    width: Int,
    height: Int,
  ): ByteArray {
    val buffer = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
    buffer.put(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    buffer.putInt(13)
    buffer.put(byteArrayOf(0x49, 0x48, 0x44, 0x52))
    buffer.putInt(width)
    buffer.putInt(height)
    return buffer.array()
  }

  private fun createSquareJpegHeader(
    width: Int,
    height: Int,
  ): ByteArray {
    val buffer = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
    buffer.put(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC0.toByte()))
    buffer.putShort(11)
    buffer.put(8)
    buffer.putShort(height.toShort())
    buffer.putShort(width.toShort())
    return buffer.array()
  }
}
