package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterResponse
import ee.tenman.portfolio.openrouter.OpenRouterVisionClient
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.testing.fixture.ImageFixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OpenRouterLogoSelectionServiceTest {
  private val openRouterVisionClient = mockk<OpenRouterVisionClient>()
  private val openRouterProperties = mockk<OpenRouterProperties>()
  private val imageDownloadService = mockk<ImageDownloadService>()
  private val logoValidationService = mockk<LogoValidationService>()
  private lateinit var service: OpenRouterLogoSelectionService

  @BeforeEach
  fun setup() {
    service =
      OpenRouterLogoSelectionService(
        openRouterVisionClient,
        openRouterProperties,
        imageDownloadService,
        logoValidationService,
      )
  }

  @Nested
  inner class SelectBestLogo {
    @Test
    fun `should return null when candidates list is empty`() {
      val result = service.selectBestLogo("Apple", "AAPL", emptyList())

      expect(result).toEqual(null)
    }

    @Test
    fun `should use fast path when title contains company name`() {
      val imageData = ImageFixtures.createPngHeader()
      val candidate = createCandidate(0, "Apple Inc Logo")
      every { imageDownloadService.download(candidate.imageUrl) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true

      val result = service.selectBestLogo("Apple", "AAPL", listOf(candidate))

      expect(result).notToEqualNull()
      expect(result?.source).toEqual(LogoSource.BING)
      expect(result?.selectedIndex).toEqual(0)
    }

    @Test
    fun `should use fast path when title contains ticker`() {
      val imageData = ImageFixtures.createPngHeader()
      val candidate = createCandidate(0, "AAPL Stock Logo")
      every { imageDownloadService.download(candidate.imageUrl) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true

      val result = service.selectBestLogo("Apple Inc", "AAPL", listOf(candidate))

      expect(result).notToEqualNull()
      expect(result?.source).toEqual(LogoSource.BING)
    }

    @Test
    fun `should return first valid when only one candidate and no fast path match`() {
      val imageData = ImageFixtures.createPngHeader()
      val candidate = createCandidate(0, "Some Other Company")
      every { imageDownloadService.download(candidate.imageUrl) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true

      val result = service.selectBestLogo("Apple", "AAPL", listOf(candidate))

      expect(result).notToEqualNull()
      expect(result?.source).toEqual(LogoSource.BING)
    }

    @Test
    fun `should skip LLM selection when API key is blank`() {
      val imageData = ImageFixtures.createPngHeader()
      val candidates =
        listOf(
          createCandidate(0, "Other Company 1"),
          createCandidate(1, "Other Company 2"),
        )
      every { openRouterProperties.apiKey } returns ""
      every { imageDownloadService.download(any()) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true

      val result = service.selectBestLogo("Apple", "AAPL", candidates)

      expect(result).notToEqualNull()
      expect(result?.source).toEqual(LogoSource.BING)
      verify(exactly = 0) { openRouterVisionClient.chatCompletion(any(), any()) }
    }

    @Test
    fun `should use LLM selection when multiple candidates and API key configured`() {
      val imageData = ImageFixtures.createPngHeader()
      val candidates =
        listOf(
          createCandidate(0, "Company A"),
          createCandidate(1, "Company B"),
        )
      val visionResponse = mockk<OpenRouterResponse>()
      every { openRouterProperties.apiKey } returns "test-api-key"
      every { openRouterProperties.visionModel } returns "test-model"
      every { openRouterProperties.apiTimeoutMs } returns 30000L
      every { imageDownloadService.download(any()) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true
      every { logoValidationService.detectMediaType(any()) } returns "image/png"
      every { openRouterVisionClient.chatCompletion(any(), any()) } returns visionResponse
      every { visionResponse.extractContent() } returns "2"

      val result = service.selectBestLogo("Apple", "AAPL", candidates)

      expect(result).notToEqualNull()
      expect(result?.source).toEqual(LogoSource.LLM_SELECTED)
      expect(result?.selectedIndex).toEqual(1)
    }

    @Test
    fun `should fall back to first valid when LLM returns invalid index`() {
      val imageData = ImageFixtures.createPngHeader()
      val candidates =
        listOf(
          createCandidate(0, "Company A"),
          createCandidate(1, "Company B"),
        )
      val visionResponse = mockk<OpenRouterResponse>()
      every { openRouterProperties.apiKey } returns "test-api-key"
      every { openRouterProperties.visionModel } returns "test-model"
      every { openRouterProperties.apiTimeoutMs } returns 30000L
      every { imageDownloadService.download(any()) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true
      every { logoValidationService.detectMediaType(any()) } returns "image/png"
      every { openRouterVisionClient.chatCompletion(any(), any()) } returns visionResponse
      every { visionResponse.extractContent() } returns "99"

      val result = service.selectBestLogo("Apple", "AAPL", candidates)

      expect(result).notToEqualNull()
      expect(result?.source).toEqual(LogoSource.BING)
    }

    @Test
    fun `should fall back to first valid when LLM call fails`() {
      val imageData = ImageFixtures.createPngHeader()
      val candidates =
        listOf(
          createCandidate(0, "Company A"),
          createCandidate(1, "Company B"),
        )
      every { openRouterProperties.apiKey } returns "test-api-key"
      every { openRouterProperties.visionModel } returns "test-model"
      every { openRouterProperties.apiTimeoutMs } returns 30000L
      every { imageDownloadService.download(any()) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true
      every { logoValidationService.detectMediaType(any()) } returns "image/png"
      every { openRouterVisionClient.chatCompletion(any(), any()) } throws RuntimeException("API error")

      val result = service.selectBestLogo("Apple", "AAPL", candidates)

      expect(result).notToEqualNull()
      expect(result?.source).toEqual(LogoSource.BING)
    }

    @Test
    fun `should return null when all downloads fail`() {
      val candidates =
        listOf(
          createCandidate(0, "Company A"),
          createCandidate(1, "Company B"),
        )
      every { openRouterProperties.apiKey } returns ""
      every { imageDownloadService.download(any()) } throws RuntimeException("Download failed")

      val result = service.selectBestLogo("Apple", "AAPL", candidates)

      expect(result).toEqual(null)
    }

    @Test
    fun `should return null when all validations fail`() {
      val imageData = ImageFixtures.createPngHeader()
      val candidates =
        listOf(
          createCandidate(0, "Company A"),
          createCandidate(1, "Company B"),
        )
      every { openRouterProperties.apiKey } returns ""
      every { imageDownloadService.download(any()) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns false

      val result = service.selectBestLogo("Apple", "AAPL", candidates)

      expect(result).toEqual(null)
    }

    @Test
    fun `should handle null ticker in fast path`() {
      val imageData = ImageFixtures.createPngHeader()
      val candidate = createCandidate(0, "Apple Logo")
      every { imageDownloadService.download(candidate.imageUrl) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true

      val result = service.selectBestLogo("Apple", null, listOf(candidate))

      expect(result).notToEqualNull()
      expect(result?.source).toEqual(LogoSource.BING)
    }

    @Test
    fun `should parse LLM response with extra text`() {
      val imageData = ImageFixtures.createPngHeader()
      val candidates =
        listOf(
          createCandidate(0, "Company A"),
          createCandidate(1, "Company B"),
        )
      val visionResponse = mockk<OpenRouterResponse>()
      every { openRouterProperties.apiKey } returns "test-api-key"
      every { openRouterProperties.visionModel } returns "test-model"
      every { openRouterProperties.apiTimeoutMs } returns 30000L
      every { imageDownloadService.download(any()) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true
      every { logoValidationService.detectMediaType(any()) } returns "image/png"
      every { openRouterVisionClient.chatCompletion(any(), any()) } returns visionResponse
      every { visionResponse.extractContent() } returns "Image 1 is the best"

      val result = service.selectBestLogo("Apple", "AAPL", candidates)

      expect(result).notToEqualNull()
      expect(result?.source).toEqual(LogoSource.LLM_SELECTED)
      expect(result?.selectedIndex).toEqual(0)
    }
  }

  private fun createCandidate(
    index: Int,
    title: String,
  ) = LogoCandidate(
    imageUrl = "https://example.com/image$index.png",
    thumbnailUrl = "https://example.com/thumb$index.png",
    title = title,
    index = index,
  )
}
