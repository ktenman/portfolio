package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LogoFallbackServiceTest {
  private val nvstlyLogoService = mockk<NvstlyLogoService>()
  private val logoValidationService = mockk<LogoValidationService>()
  private val imageDownloadService = mockk<ImageDownloadService>()
  private lateinit var service: LogoFallbackService

  @BeforeEach
  fun setUp() {
    service =
      LogoFallbackService(
        nvstlyLogoService,
        logoValidationService,
        imageDownloadService,
      )
  }

  @Test
  fun `should return lightyear logo when available and valid`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { imageDownloadService.download("https://lightyear.com/logo.png") } returns imageData
    every { logoValidationService.isValidLogo(imageData) } returns true

    val result = service.fetchLogo("Apple Inc", "AAPL", "https://lightyear.com/logo.png")

    expect(result?.source).toEqual(LogoSource.LIGHTYEAR)
    expect(result?.imageData).toEqual(imageData)
    verify(exactly = 0) { nvstlyLogoService.fetchLogo(any()) }
  }

  @Test
  fun `should skip lightyear and try nvstly when lightyear url is null`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { nvstlyLogoService.fetchLogo("AAPL") } returns imageData
    every { logoValidationService.isValidLogo(imageData) } returns true

    val result = service.fetchLogo("Apple Inc", "AAPL", null)

    expect(result?.source).toEqual(LogoSource.NVSTLY_ICONS)
    expect(result?.ticker).toEqual("AAPL")
  }

  @Test
  fun `should return null when no ticker provided and no lightyear url`() {
    val result = service.fetchLogo("Apple Inc", null, null)

    expect(result).toEqual(null)
    verify(exactly = 0) { nvstlyLogoService.fetchLogo(any()) }
  }

  @Test
  fun `should return null when nvstly fails and no lightyear url`() {
    every { nvstlyLogoService.fetchLogo("AAPL") } returns null

    val result = service.fetchLogo("Apple Inc", "AAPL", null)

    expect(result).toEqual(null)
  }

  @Test
  fun `should skip logo when validation fails`() {
    val invalidData = byteArrayOf(1, 2, 3, 4)
    val validData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { imageDownloadService.download("https://lightyear.com/logo.png") } returns invalidData
    every { logoValidationService.isValidLogo(invalidData) } returns false
    every { nvstlyLogoService.fetchLogo("AAPL") } returns validData
    every { logoValidationService.isValidLogo(validData) } returns true

    val result = service.fetchLogo("Apple Inc", "AAPL", "https://lightyear.com/logo.png")

    expect(result?.source).toEqual(LogoSource.NVSTLY_ICONS)
  }

  @Test
  fun `should handle lightyear download failure gracefully`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { imageDownloadService.download("https://lightyear.com/logo.png") } throws RuntimeException("Network error")
    every { nvstlyLogoService.fetchLogo("AAPL") } returns imageData
    every { logoValidationService.isValidLogo(imageData) } returns true

    val result = service.fetchLogo("Apple Inc", "AAPL", "https://lightyear.com/logo.png")

    expect(result?.source).toEqual(LogoSource.NVSTLY_ICONS)
  }

  @Test
  fun `should use existing ticker for nvstly lookup`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { nvstlyLogoService.fetchLogo("EXISTING") } returns imageData
    every { logoValidationService.isValidLogo(imageData) } returns true

    val result = service.fetchLogo("Apple Inc", "EXISTING", null)

    expect(result?.ticker).toEqual("EXISTING")
    expect(result?.source).toEqual(LogoSource.NVSTLY_ICONS)
  }
}
