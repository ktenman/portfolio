package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.cause
import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.exception.LogoSelectionFailedException
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterResponse
import ee.tenman.portfolio.openrouter.OpenRouterVisionClient
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest.ImageContent
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest.TextContent
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.testing.fixture.ImageFixtures
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
      expect(result?.source).toEqual(LogoSource.DUCKDUCKGO)
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
      expect(result?.source).toEqual(LogoSource.DUCKDUCKGO)
    }

    @Test
    fun `should ask the LLM about a single candidate without a fast path match`() {
      llmAnswers("1")

      val result = service.selectBestLogo("Apple", "AAPL", listOf(createCandidate(0, "Some Other Company")))

      expect(result?.source).toEqual(LogoSource.LLM_SELECTED)
    }

    @Test
    fun `should fail when the API key is blank`() {
      val candidates = twoCandidates()
      every { openRouterProperties.apiKey } returns ""

      expect { service.selectBestLogo("Apple", "AAPL", candidates) }.toThrow<LogoSelectionFailedException>()
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
    fun `should fail when the LLM answers beyond the last image`() {
      val candidates = twoCandidates()
      llmAnswers("99")

      expect { service.selectBestLogo("Apple", "AAPL", candidates) }
        .toThrow<LogoSelectionFailedException>()
        .messageToContain("unparsable")
    }

    @Test
    fun `should fail when the LLM call fails`() {
      val candidates = twoCandidates()
      llmAnswers("1")
      every { openRouterVisionClient.chatCompletion(any(), any()) } throws IllegalStateException("API error")

      expect { service.selectBestLogo("Apple", "AAPL", candidates) }
        .toThrow<LogoSelectionFailedException>()
        .cause<IllegalStateException>()
        .messageToContain("API error")
    }

    @Test
    fun `should fail when the LLM call times out`() {
      val candidates = twoCandidates()
      llmAnswers("1")
      every { openRouterProperties.apiTimeoutMs } returns 0L

      expect { service.selectBestLogo("Apple", "AAPL", candidates) }
        .toThrow<LogoSelectionFailedException>()
        .messageToContain("timed out")
    }

    @Test
    fun `should return null when all downloads fail`() {
      val candidates =
        listOf(
          createCandidate(0, "Company A"),
          createCandidate(1, "Company B"),
        )
      every { openRouterProperties.apiKey } returns "test-api-key"
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
      every { openRouterProperties.apiKey } returns "test-api-key"
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
      expect(result?.source).toEqual(LogoSource.DUCKDUCKGO)
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

    @Test
    fun `should return null when the LLM answers zero`() {
      llmAnswers("0")

      val result = service.selectBestLogo("Apple", "AAPL", twoCandidates())

      expect(result).toEqual(null)
    }

    @Test
    fun `should return null when the LLM explains its zero`() {
      llmAnswers("0 - none of them")

      val result = service.selectBestLogo("Apple", "AAPL", twoCandidates())

      expect(result).toEqual(null)
    }

    @Test
    fun `should return null when the LLM counts the images after its zero`() {
      val candidates = (0..4).map { createCandidate(it, "Company $it") }
      llmAnswers("0 - none of the 5 images")

      val result = service.selectBestLogo("Apple", "AAPL", candidates)

      expect(result).toEqual(null)
    }

    @Test
    fun `should fail when the LLM reply is blank`() {
      val candidates = twoCandidates()
      llmAnswers(null)

      expect { service.selectBestLogo("Apple", "AAPL", candidates) }
        .toThrow<LogoSelectionFailedException>()
        .messageToContain("blank")
    }

    @Test
    fun `should fail when the LLM reply holds no number`() {
      val candidates = twoCandidates()
      llmAnswers("none")

      expect { service.selectBestLogo("Apple", "AAPL", candidates) }
        .toThrow<LogoSelectionFailedException>()
        .messageToContain("unparsable")
    }

    @Test
    fun `should allow the LLM 200 tokens`() {
      val request = slot<OpenRouterVisionRequest>()
      llmAnswers("1", request)

      service.selectBestLogo("Apple", "AAPL", twoCandidates())

      expect(request.captured.maxTokens).toEqual(200)
    }

    @Test
    fun `should tell the LLM it may answer zero`() {
      val request = slot<OpenRouterVisionRequest>()
      llmAnswers("1", request)

      service.selectBestLogo("Apple", "AAPL", twoCandidates())

      expect(request.texts().first()).toContain("or 0 if none of them is this company's logo")
    }

    @Test
    fun `should send each title after its image`() {
      val request = slot<OpenRouterVisionRequest>()
      val candidates = listOf(createCandidate(0, "Põhja Pank logo"), createCandidate(1, " "))
      llmAnswers("1", request)

      service.selectBestLogo("Apple", "AAPL", candidates)

      expect(request.texts().drop(1)).toContainExactly("Image 1: Põhja Pank logo", "Image 2")
    }

    @Test
    fun `should offer the LLM five valid candidates after broken ones`() {
      val request = slot<OpenRouterVisionRequest>()
      val candidates = (0..7).map { createCandidate(it, "Company $it") }
      llmAnswers("1", request)
      candidates.take(2).forEach {
        every { imageDownloadService.download(it.imageUrl) } throws RuntimeException("Broken link")
      }

      service.selectBestLogo("Apple", "AAPL", candidates)

      verify(exactly = 0) { imageDownloadService.download(candidates[7].imageUrl) }
      expect(request.parts().filterIsInstance<ImageContent>()).toHaveSize(5)
    }
  }

  private fun llmAnswers(
    reply: String?,
    request: CapturingSlot<OpenRouterVisionRequest> = slot(),
  ) {
    val imageData = ImageFixtures.createPngHeader()
    val response = OpenRouterResponse(listOf(OpenRouterResponse.Choice(OpenRouterResponse.Message(content = reply))))
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterProperties.visionModel } returns "test-model"
    every { openRouterProperties.apiTimeoutMs } returns 30000L
    every { imageDownloadService.download(any()) } returns imageData
    every { logoValidationService.isValidLogo(imageData) } returns true
    every { logoValidationService.detectMediaType(any()) } returns "image/png"
    every { openRouterVisionClient.chatCompletion(any(), capture(request)) } returns response
  }

  private fun CapturingSlot<OpenRouterVisionRequest>.parts() = captured.messages.single().content

  private fun CapturingSlot<OpenRouterVisionRequest>.texts() = parts().filterIsInstance<TextContent>().map { it.text }

  private fun twoCandidates() = listOf(createCandidate(0, "Company A"), createCandidate(1, "Company B"))

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
