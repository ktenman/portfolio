package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DetectionProvider
import ee.tenman.portfolio.googlevision.GoogleVisionService
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest
import ee.tenman.portfolio.openrouter.OpenRouterVisionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class LicensePlateDetectionServiceTest {
  private val googleVisionService = mockk<GoogleVisionService>()
  private val openRouterVisionService = mockk<OpenRouterVisionService>()
  private val openRouterProperties = mockk<OpenRouterProperties>()

  private lateinit var service: LicensePlateDetectionService

  @BeforeEach
  fun setUp() {
    service = LicensePlateDetectionService(googleVisionService, openRouterVisionService, openRouterProperties)
  }

  @Test
  fun `should return plate number from Google Vision when detected`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns
      mapOf("hasCar" to "true", "plateNumber" to "123ABC")

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("123ABC")
    expect(result.hasCar).toEqual(true)
    expect(result.provider).toEqual(DetectionProvider.GOOGLE_VISION)
    verify(exactly = 0) { openRouterVisionService.extractText(any()) }
  }

  @Test
  fun `should try OpenRouter fallback even when Google Vision detects no car`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "false")
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns "456DEF"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("456DEF")
    expect(result.hasCar).toEqual(true)
    expect(result.provider).toEqual(DetectionProvider.GEMINI_FLASH_LITE)
    verify(exactly = 1) { openRouterVisionService.extractText(any()) }
  }

  @Test
  fun `should return no plate when all providers fail and no car detected`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "false")
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns null

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual(null)
    expect(result.hasCar).toEqual(false)
    expect(result.provider).toEqual(DetectionProvider.ALL_FAILED)
  }

  @Test
  fun `should fallback to Gemini Flash Lite when Google Vision detects no plate`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "true")
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns "456DEF"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("456DEF")
    expect(result.hasCar).toEqual(true)
    expect(result.provider).toEqual(DetectionProvider.GEMINI_FLASH_LITE)
    verify(exactly = 1) { openRouterVisionService.extractText(any()) }
  }

  @Test
  fun `should fallback to Pixtral when Gemini Flash Lite fails`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "true")
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(match { it.model == "google/gemini-2.5-flash-lite" }) } returns null
    every { openRouterVisionService.extractText(match { it.model == "mistralai/pixtral-12b" }) } returns "789GHI"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("789GHI")
    expect(result.hasCar).toEqual(true)
    expect(result.provider).toEqual(DetectionProvider.PIXTRAL)
    verify(exactly = 2) { openRouterVisionService.extractText(any()) }
  }

  @Test
  fun `should return null when all models fail to detect plate`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "true")
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns null

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual(null)
    expect(result.hasCar).toEqual(true)
    expect(result.provider).toEqual(DetectionProvider.ALL_FAILED)
    verify(exactly = 2) { openRouterVisionService.extractText(any()) }
  }

  @Test
  fun `should skip OpenRouter fallback when API key is blank`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "false")
    every { openRouterProperties.apiKey } returns ""

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual(null)
    expect(result.hasCar).toEqual(false)
    expect(result.provider).toEqual(DetectionProvider.GOOGLE_VISION)
    verify(exactly = 0) { openRouterVisionService.extractText(any()) }
  }

  @Test
  fun `should extract plate number from response with spaces`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "true")
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns "678 WKS"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("678WKS")
    expect(result.provider).toEqual(DetectionProvider.GEMINI_FLASH_LITE)
  }

  @Test
  fun `should return null when response does not match plate pattern`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "true")
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns "No plate visible"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual(null)
    expect(result.provider).toEqual(DetectionProvider.ALL_FAILED)
  }

  @Test
  fun `should handle lowercase plate response`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "true")
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns "123abc"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("123ABC")
    expect(result.provider).toEqual(DetectionProvider.GEMINI_FLASH_LITE)
  }

  @Test
  fun `should build correct request for license plate extraction`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    val requestSlot = slot<OpenRouterVisionRequest>()
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "true")
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(capture(requestSlot)) } returns "123ABC"

    service.detectPlateNumber(base64Image, uuid)

    expect(requestSlot.captured.model).toEqual("google/gemini-2.5-flash-lite")
    expect(requestSlot.captured.maxTokens).toEqual(50)
  }
}
