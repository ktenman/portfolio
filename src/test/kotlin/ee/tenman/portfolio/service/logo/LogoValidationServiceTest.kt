package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.testing.fixture.ImageFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LogoValidationServiceTest {
  private lateinit var service: LogoValidationService

  @BeforeEach
  fun setUp() {
    service = LogoValidationService()
  }

  @Nested
  inner class DetectMediaType {
    @Test
    fun `should detect PNG media type`() {
      val pngData = ImageFixtures.createPngHeader(100, 100)

      expect(service.detectMediaType(pngData)).toEqual(LogoValidationService.MEDIA_TYPE_PNG)
    }

    @Test
    fun `should detect JPEG media type`() {
      val jpegData = ImageFixtures.createJpegHeader(100, 100, 0xC0)

      expect(service.detectMediaType(jpegData)).toEqual(LogoValidationService.MEDIA_TYPE_JPEG)
    }

    @Test
    fun `should detect WebP media type`() {
      val webpData = ImageFixtures.createVp8WebPHeader(100, 100)

      expect(service.detectMediaType(webpData)).toEqual(LogoValidationService.MEDIA_TYPE_WEBP)
    }

    @Test
    fun `should default to PNG for unknown format`() {
      val unknownData = "Hello, World!".toByteArray()

      expect(service.detectMediaType(unknownData)).toEqual(LogoValidationService.MEDIA_TYPE_PNG)
    }

    @Test
    fun `should default to PNG for empty data`() {
      expect(service.detectMediaType(byteArrayOf())).toEqual(LogoValidationService.MEDIA_TYPE_PNG)
    }
  }

  @Nested
  inner class EmptyAndUnknownFormats {
    @Test
    fun `should return false for empty data`() {
      expect(service.isValidLogo(byteArrayOf())).toEqual(false)
    }

    @Test
    fun `should return false for data too small to be an image`() {
      expect(service.isValidLogo(byteArrayOf(1, 2, 3))).toEqual(false)
    }

    @Test
    fun `should return false for non-image data`() {
      val textData = "Hello, World!".toByteArray()

      expect(service.isValidLogo(textData)).toEqual(false)
    }
  }

  @Nested
  inner class PngValidation {
    @Test
    fun `should validate square PNG image`() {
      val pngData = ImageFixtures.createPngHeader(100, 100)

      expect(service.isValidLogo(pngData)).toEqual(true)
    }

    @Test
    fun `should reject non-square PNG image`() {
      val pngData = ImageFixtures.createPngHeader(200, 100)

      expect(service.isValidLogo(pngData)).toEqual(false)
    }

    @Test
    fun `should accept slightly non-square PNG within tolerance`() {
      val pngData = ImageFixtures.createPngHeader(100, 105)

      expect(service.isValidLogo(pngData)).toEqual(true)
    }

    @Test
    fun `should reject PNG image too small`() {
      val pngData = ImageFixtures.createPngHeader(20, 20)

      expect(service.isValidLogo(pngData)).toEqual(false)
    }

    @Test
    fun `should reject PNG outside aspect ratio tolerance`() {
      val pngData = ImageFixtures.createPngHeader(100, 115)

      expect(service.isValidLogo(pngData)).toEqual(false)
    }

    @Test
    fun `should return false for truncated PNG`() {
      val truncatedPng =
        byteArrayOf(
          0x89.toByte(),
          0x50.toByte(),
          0x4E.toByte(),
          0x47.toByte(),
          0x0D.toByte(),
          0x0A.toByte(),
          0x1A.toByte(),
          0x0A.toByte(),
        )

      expect(service.isValidLogo(truncatedPng)).toEqual(false)
    }

    @Test
    fun `should handle large PNG dimensions`() {
      val largePng = ImageFixtures.createPngHeader(1024, 1024)

      expect(service.isValidLogo(largePng)).toEqual(true)
    }
  }

  @Nested
  inner class JpegValidation {
    @Test
    fun `should validate square JPEG image`() {
      val jpegData = ImageFixtures.createJpegHeader(100, 100, 0xC0)

      expect(service.isValidLogo(jpegData)).toEqual(true)
    }

    @Test
    fun `should reject non-square JPEG image`() {
      val jpegData = ImageFixtures.createJpegHeader(200, 100, 0xC0)

      expect(service.isValidLogo(jpegData)).toEqual(false)
    }

    @Test
    fun `should validate progressive JPEG with C2 marker`() {
      val jpegData = ImageFixtures.createJpegHeader(100, 100, 0xC2)

      expect(service.isValidLogo(jpegData)).toEqual(true)
    }

    @Test
    fun `should return false for truncated JPEG`() {
      val truncatedJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

      expect(service.isValidLogo(truncatedJpeg)).toEqual(false)
    }

    @Test
    fun `should return false when JPEG has no SOF marker`() {
      val noSofJpeg =
        byteArrayOf(
          0xFF.toByte(),
          0xD8.toByte(),
          0xFF.toByte(),
          0xE0.toByte(),
          0x00.toByte(),
          0x10.toByte(),
        ) + ByteArray(20)

      expect(service.isValidLogo(noSofJpeg)).toEqual(false)
    }
  }

  @Nested
  inner class WebPValidation {
    @Test
    fun `should validate VP8 lossy WebP`() {
      val webpData = ImageFixtures.createVp8WebPHeader(100, 100)

      expect(service.isValidLogo(webpData)).toEqual(true)
    }

    @Test
    fun `should reject non-square VP8 lossy WebP`() {
      val webpData = ImageFixtures.createVp8WebPHeader(200, 100)

      expect(service.isValidLogo(webpData)).toEqual(false)
    }

    @Test
    fun `should validate VP8L lossless WebP`() {
      val webpData = ImageFixtures.createVp8LWebPHeader(100, 100)

      expect(service.isValidLogo(webpData)).toEqual(true)
    }

    @Test
    fun `should reject non-square VP8L WebP`() {
      val webpData = ImageFixtures.createVp8LWebPHeader(200, 100)

      expect(service.isValidLogo(webpData)).toEqual(false)
    }

    @Test
    fun `should validate VP8X extended WebP`() {
      val webpData = ImageFixtures.createVp8XWebPHeader(100, 100)

      expect(service.isValidLogo(webpData)).toEqual(true)
    }

    @Test
    fun `should reject non-square VP8X WebP`() {
      val webpData = ImageFixtures.createVp8XWebPHeader(200, 100)

      expect(service.isValidLogo(webpData)).toEqual(false)
    }

    @Test
    fun `should return false for truncated WebP`() {
      val truncatedWebp =
        byteArrayOf(
          0x52.toByte(),
          0x49.toByte(),
          0x46.toByte(),
          0x46.toByte(),
          0x00.toByte(),
          0x00.toByte(),
          0x00.toByte(),
          0x00.toByte(),
          0x57.toByte(),
          0x45.toByte(),
          0x42.toByte(),
          0x50.toByte(),
        )

      expect(service.isValidLogo(truncatedWebp)).toEqual(false)
    }

    @Test
    fun `should return false for WebP with unknown subformat`() {
      val unknownWebp = ImageFixtures.createWebPWithSubformat(0x00.toByte(), 100, 100)

      expect(service.isValidLogo(unknownWebp)).toEqual(false)
    }
  }
}
