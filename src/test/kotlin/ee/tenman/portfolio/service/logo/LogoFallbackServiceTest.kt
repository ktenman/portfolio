package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.exception.LogoSearchUnavailableException
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LogoFallbackServiceTest {
  private val imageSearchLogoService = mockk<ImageSearchLogoService>()
  private val logoValidationService = mockk<LogoValidationService>()
  private val imageDownloadService = mockk<ImageDownloadService>()
  private val openRouterLogoSelectionService = mockk<OpenRouterLogoSelectionService>()
  private lateinit var service: LogoFallbackService

  @BeforeEach
  fun setUp() {
    service =
      LogoFallbackService(
        imageSearchLogoService,
        logoValidationService,
        imageDownloadService,
        openRouterLogoSelectionService,
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
    verify(exactly = 0) { imageDownloadService.downloadOrNull(any()) }
  }

  @Test
  fun `should return the Parqet logo of the ticker when lightyear url is null`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { imageDownloadService.downloadOrNull(PARQET_AAPL) } returns imageData
    every { logoValidationService.isValidLogo(imageData) } returns true

    val result = service.fetchLogo("Apple Inc", "AAPL", null)

    expect(result?.source).toEqual(LogoSource.PARQET)
  }

  @Test
  fun `should fall back to nvstly when Parqet has no logo of the ticker`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { imageDownloadService.downloadOrNull(PARQET_AAPL) } returns null
    every { imageDownloadService.downloadOrNull(NVSTLY_AAPL) } returns imageData
    every { logoValidationService.isValidLogo(imageData) } returns true

    val result = service.fetchLogo("Apple Inc", "AAPL", null)

    expect(result?.source).toEqual(LogoSource.NVSTLY_ICONS)
  }

  @Test
  fun `should skip the ticker logos and fallback to llm selection when no ticker provided`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    val candidates = listOf(LogoCandidate("http://img.com", "http://thumb.com", "Apple Logo", 0))
    every { imageSearchLogoService.searchLogoCandidates("Apple Inc") } returns candidates
    every { openRouterLogoSelectionService.selectBestLogo("Apple Inc", null, candidates) } returns
      LogoSelectionResult(0, imageData, LogoSource.LLM_SELECTED)

    val result = service.fetchLogo("Apple Inc", null, null)

    expect(result?.source).toEqual(LogoSource.LLM_SELECTED)
    verify(exactly = 0) { imageDownloadService.downloadOrNull(any()) }
  }

  @Test
  fun `should fallback to llm selection when the ticker logos fail`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    val candidates = listOf(LogoCandidate("http://img.com", "http://thumb.com", "Apple Logo", 0))
    every { imageDownloadService.downloadOrNull(any()) } returns null
    every { imageSearchLogoService.searchLogoCandidates("AAPL Apple Inc") } returns candidates
    every { openRouterLogoSelectionService.selectBestLogo("Apple Inc", "AAPL", candidates) } returns
      LogoSelectionResult(0, imageData, LogoSource.DUCKDUCKGO)

    val result = service.fetchLogo("Apple Inc", "AAPL", null)

    expect(result?.source).toEqual(LogoSource.DUCKDUCKGO)
    expect(result?.ticker).toEqual("AAPL")
  }

  @Test
  fun `should return null when all sources fail`() {
    every { imageDownloadService.downloadOrNull(any()) } returns null
    every { imageSearchLogoService.searchLogoCandidates("AAPL Apple Inc") } returns emptyList()

    val result = service.fetchLogo("Apple Inc", "AAPL", null)

    expect(result).toEqual(null)
  }

  @Test
  fun `should let an unavailable search reach the caller`() {
    every { imageSearchLogoService.searchLogoCandidates("Äri AS") } throws LogoSearchUnavailableException("DuckDuckGo request was blocked")

    expect { service.fetchLogo("Äri AS", null, null) }.toThrow<LogoSearchUnavailableException>()
  }

  @Test
  fun `should skip logo when validation fails`() {
    val invalidData = byteArrayOf(1, 2, 3, 4)
    val validData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { imageDownloadService.download("https://lightyear.com/logo.png") } returns invalidData
    every { logoValidationService.isValidLogo(invalidData) } returns false
    every { imageDownloadService.downloadOrNull(PARQET_AAPL) } returns validData
    every { logoValidationService.isValidLogo(validData) } returns true

    val result = service.fetchLogo("Apple Inc", "AAPL", "https://lightyear.com/logo.png")

    expect(result?.source).toEqual(LogoSource.PARQET)
  }

  @Test
  fun `should try nvstly when the Parqet logo fails validation`() {
    val invalidData = byteArrayOf(1, 2, 3, 4)
    val validData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { imageDownloadService.downloadOrNull(PARQET_AAPL) } returns invalidData
    every { logoValidationService.isValidLogo(invalidData) } returns false
    every { imageDownloadService.downloadOrNull(NVSTLY_AAPL) } returns validData
    every { logoValidationService.isValidLogo(validData) } returns true

    val result = service.fetchLogo("Apple Inc", "AAPL", null)

    expect(result?.source).toEqual(LogoSource.NVSTLY_ICONS)
  }

  @Test
  fun `should handle lightyear download failure gracefully`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { imageDownloadService.download("https://lightyear.com/logo.png") } throws RuntimeException("Network error")
    every { imageDownloadService.downloadOrNull(PARQET_AAPL) } returns imageData
    every { logoValidationService.isValidLogo(imageData) } returns true

    val result = service.fetchLogo("Apple Inc", "AAPL", "https://lightyear.com/logo.png")

    expect(result?.source).toEqual(LogoSource.PARQET)
  }

  @Test
  fun `should keep the ticker the logo was found by`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { imageDownloadService.downloadOrNull("https://assets.parqet.com/logos/symbol/ÄRI?format=png&size=512") } returns imageData
    every { logoValidationService.isValidLogo(imageData) } returns true

    val result = service.fetchLogo("Äri AS", "äri", null)

    expect(result?.ticker).toEqual("äri")
  }

  companion object {
    private const val PARQET_AAPL = "https://assets.parqet.com/logos/symbol/AAPL?format=png&size=512"
    private const val NVSTLY_AAPL = "https://raw.githubusercontent.com/nvstly/icons/main/ticker_icons/AAPL.png"
  }
}
