package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.BatchLogoValidationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
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
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.UUID

class BatchLogoValidationServiceTest {
  private val openRouterVisionClient = mockk<OpenRouterVisionClient>()
  private val openRouterProperties = mockk<OpenRouterProperties>()
  private val batchProperties =
    BatchLogoValidationProperties(
    enabled = true,
    model = AiModel.GEMINI_3_FLASH_PREVIEW,
    batchSize = 25,
    imagesPerCompany = 10,
  )
  private val imageSearchLogoService = mockk<ImageSearchLogoService>()
  private val imageDownloadService = mockk<ImageDownloadService>()
  private val logoValidationService = mockk<LogoValidationService>()
  private val objectMapper: ObjectMapper =
    JsonMapper
      .builder()
    .addModule(KotlinModule.Builder().build())
    .build()

  private lateinit var service: BatchLogoValidationService

  @BeforeEach
  fun setup() {
    service =
      BatchLogoValidationService(
      openRouterVisionClient,
      openRouterProperties,
      batchProperties,
      imageSearchLogoService,
      imageDownloadService,
      logoValidationService,
      objectMapper,
    )
  }

  @Nested
  inner class ValidateBatch {
    @Test
    fun `should return empty list when holdings list is empty`() {
      val result = service.validateBatch(emptyList())

      expect(result).toBeEmpty()
    }

    @Test
    fun `should return empty list when batch validation is disabled`() {
      val disabledProperties = BatchLogoValidationProperties(enabled = false)
      val disabledService =
        BatchLogoValidationService(
        openRouterVisionClient,
        openRouterProperties,
        disabledProperties,
        imageSearchLogoService,
        imageDownloadService,
        logoValidationService,
        objectMapper,
      )
      val holdings = listOf(createHolding("Apple Inc", "AAPL"))

      val result = disabledService.validateBatch(holdings)

      expect(result).toBeEmpty()
    }

    @Test
    fun `should return empty list when API key is blank`() {
      every { openRouterProperties.apiKey } returns ""
      val holdings = listOf(createHolding("Apple Inc", "AAPL"))

      val result = service.validateBatch(holdings)

      expect(result).toBeEmpty()
    }

    @Test
    fun `should validate batch successfully`() {
      val imageData = ImageFixtures.createPngHeader()
      val holding = createHolding("Apple Inc", "AAPL")
      val candidate =
        LogoCandidate(
        imageUrl = "https://example.com/apple.png",
        thumbnailUrl = "https://example.com/apple-thumb.png",
        title = "Apple Logo",
        index = 0,
      )
      val visionResponse = mockk<OpenRouterResponse>()
      every { openRouterProperties.apiKey } returns "test-api-key"
      every { imageSearchLogoService.searchLogoCandidates(any(), any()) } returns listOf(candidate)
      every { imageDownloadService.downloadOrNull(any()) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true
      every { logoValidationService.detectMediaType(imageData) } returns "image/png"
      every { openRouterVisionClient.chatCompletion(any(), any()) } returns visionResponse
      every { visionResponse.extractContent() } returns """{"1": [1]}"""

      val result = service.validateBatch(listOf(holding))

      expect(result).toHaveSize(1)
      expect(result[0].companyName).toEqual("Apple Inc")
      expect(result[0].ticker).toEqual("AAPL")
      expect(result[0].validCandidateIndices).toContainExactly(0)
    }

    @Test
    fun `should handle empty valid indices from LLM`() {
      val imageData = ImageFixtures.createPngHeader()
      val holding = createHolding("Unknown Corp", "UNK")
      val candidate =
        LogoCandidate(
        imageUrl = "https://example.com/unknown.png",
        thumbnailUrl = "https://example.com/unknown-thumb.png",
        title = "Some Image",
        index = 0,
      )
      val visionResponse = mockk<OpenRouterResponse>()
      every { openRouterProperties.apiKey } returns "test-api-key"
      every { imageSearchLogoService.searchLogoCandidates(any(), any()) } returns listOf(candidate)
      every { imageDownloadService.downloadOrNull(any()) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true
      every { logoValidationService.detectMediaType(imageData) } returns "image/png"
      every { openRouterVisionClient.chatCompletion(any(), any()) } returns visionResponse
      every { visionResponse.extractContent() } returns """{"1": []}"""

      val result = service.validateBatch(listOf(holding))

      expect(result).toHaveSize(1)
      expect(result[0].validCandidateIndices).toBeEmpty()
    }

    @Test
    fun `should handle API call failure gracefully`() {
      val imageData = ImageFixtures.createPngHeader()
      val holding = createHolding("Apple Inc", "AAPL")
      val candidate =
        LogoCandidate(
        imageUrl = "https://example.com/apple.png",
        thumbnailUrl = "https://example.com/apple-thumb.png",
        title = "Apple Logo",
        index = 0,
      )
      every { openRouterProperties.apiKey } returns "test-api-key"
      every { imageSearchLogoService.searchLogoCandidates(any(), any()) } returns listOf(candidate)
      every { imageDownloadService.downloadOrNull(any()) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true
      every { logoValidationService.detectMediaType(imageData) } returns "image/png"
      every { openRouterVisionClient.chatCompletion(any(), any()) } throws RuntimeException("API error")

      val result = service.validateBatch(listOf(holding))

      expect(result).toHaveSize(1)
      expect(result[0].validCandidateIndices).toBeEmpty()
    }

    @Test
    fun `should skip holdings with no valid candidates`() {
      val holding = createHolding("Unknown Corp", "UNK")
      every { openRouterProperties.apiKey } returns "test-api-key"
      every { imageSearchLogoService.searchLogoCandidates(any(), any()) } returns emptyList()

      val result = service.validateBatch(listOf(holding))

      expect(result).toBeEmpty()
      verify(exactly = 0) { openRouterVisionClient.chatCompletion(any(), any()) }
    }

    @Test
    fun `should handle multiple companies in batch`() {
      val imageData = ImageFixtures.createPngHeader()
      val holdings =
        listOf(
        createHolding("Apple Inc", "AAPL"),
        createHolding("Microsoft Corp", "MSFT"),
      )
      val candidate1 =
        LogoCandidate(
        imageUrl = "https://example.com/apple.png",
        thumbnailUrl = "https://example.com/apple-thumb.png",
        title = "Apple Logo",
        index = 0,
      )
      val candidate2 =
        LogoCandidate(
        imageUrl = "https://example.com/msft.png",
        thumbnailUrl = "https://example.com/msft-thumb.png",
        title = "Microsoft Logo",
        index = 0,
      )
      val visionResponse = mockk<OpenRouterResponse>()
      every { openRouterProperties.apiKey } returns "test-api-key"
      every { imageSearchLogoService.searchLogoCandidates("AAPL Apple Inc logo", any()) } returns listOf(candidate1)
      every { imageSearchLogoService.searchLogoCandidates("MSFT Microsoft Corp logo", any()) } returns listOf(candidate2)
      every { imageDownloadService.downloadOrNull(any()) } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true
      every { logoValidationService.detectMediaType(imageData) } returns "image/png"
      every { openRouterVisionClient.chatCompletion(any(), any()) } returns visionResponse
      every { visionResponse.extractContent() } returns """{"1": [1], "2": [1]}"""

      val result = service.validateBatch(holdings)

      expect(result).toHaveSize(2)
      expect(result[0].companyName).toEqual("Apple Inc")
      expect(result[0].validCandidateIndices).toContainExactly(0)
      expect(result[1].companyName).toEqual("Microsoft Corp")
      expect(result[1].validCandidateIndices).toContainExactly(0)
    }
  }

  private fun createHolding(
    name: String,
    ticker: String?,
  ): EtfHolding =
    EtfHolding(ticker = ticker, name = name).apply {
      this.id = 1L
      this.uuid = UUID.randomUUID()
    }
}
