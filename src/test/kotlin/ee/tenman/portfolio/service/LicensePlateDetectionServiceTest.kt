package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.DetectionProvider
import ee.tenman.portfolio.googlevision.GoogleVisionService
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterVisionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class LicensePlateDetectionServiceTest {
  private val googleVisionService = mockk<GoogleVisionService>()
  private val openRouterVisionService = mockk<OpenRouterVisionService>()
  private val openRouterProperties = mockk<OpenRouterProperties>()
  private val dispatcher = Dispatchers.Default

  private lateinit var service: LicensePlateDetectionService

  @BeforeEach
  fun setUp() {
    service = LicensePlateDetectionService(
      googleVisionService,
      openRouterVisionService,
      openRouterProperties,
      dispatcher,
    )
  }

  @Test
  fun `should return plate when any provider succeeds`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns "123ABC"
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns
      mapOf("hasCar" to "true", "plateNumber" to "123ABC")

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("123ABC")
    expect(result.hasCar).toEqual(true)
  }

  @Test
  fun `should return plate from OpenRouter when it responds first`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns "456DEF"
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "false")

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("456DEF")
    expect(result.provider).notToEqual(DetectionProvider.GOOGLE_VISION)
    expect(result.provider).notToEqual(DetectionProvider.ALL_FAILED)
  }

  @Test
  fun `should return plate from Google Vision when OpenRouter models fail`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns null
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns
      mapOf("hasCar" to "true", "plateNumber" to "111AAA")

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("111AAA")
    expect(result.hasCar).toEqual(true)
    expect(result.provider).toEqual(DetectionProvider.GOOGLE_VISION)
  }

  @Test
  fun `should use only Google Vision when OpenRouter API key is blank`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns ""
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns
      mapOf("hasCar" to "true", "plateNumber" to "222BBB")

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("222BBB")
    expect(result.provider).toEqual(DetectionProvider.GOOGLE_VISION)
    verify(exactly = 0) { openRouterVisionService.extractText(any()) }
  }

  @Test
  fun `should return no plate when all providers fail`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns null
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "false")

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual(null)
    expect(result.hasCar).toEqual(false)
    expect(result.provider).toEqual(DetectionProvider.ALL_FAILED)
  }

  @Test
  fun `should extract plate number from response with spaces`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns "678 WKS"
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "false")

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("678WKS")
  }

  @Test
  fun `should handle lowercase plate response`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns "123abc"
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "false")

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("123ABC")
  }

  @Test
  fun `should continue with other providers when one throws exception`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(match { it.model == "mistralai/pixtral-12b" }) } throws RuntimeException("API error")
    every { openRouterVisionService.extractText(match { it.model == "google/gemini-2.5-flash" }) } returns "333CCC"
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "false")

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("333CCC")
  }

  @Test
  fun `should return null when response does not match plate pattern`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns "No plate visible"
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "true")

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual(null)
    expect(result.provider).toEqual(DetectionProvider.ALL_FAILED)
  }

  @Test
  fun `should run all providers in parallel`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns "test-api-key"
    every { openRouterVisionService.extractText(any()) } returns null
    every { googleVisionService.getPlateNumber(base64Image, uuid) } returns mapOf("hasCar" to "false")

    service.detectPlateNumber(base64Image, uuid)

    verify(exactly = 1) { googleVisionService.getPlateNumber(base64Image, uuid) }
    verify(exactly = 2) { openRouterVisionService.extractText(any()) }
  }
}
