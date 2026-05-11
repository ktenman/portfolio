package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.VisionModel
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
  private val openRouterVisionService = mockk<OpenRouterVisionService>()
  private val openRouterProperties = mockk<OpenRouterProperties>()
  private val dispatcher = Dispatchers.Default

  private lateinit var service: LicensePlateDetectionService

  @BeforeEach
  fun setUp() {
    every { openRouterProperties.apiKey } returns "test-api-key"
    service =
      LicensePlateDetectionService(
        openRouterVisionService,
        openRouterProperties,
        dispatcher,
      )
  }

  @Test
  fun `should return plate when OpenRouter succeeds`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterVisionService.extractText(any()) } returns "123ABC"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("123ABC")
    expect(result.provider).notToEqual(null)
  }

  @Test
  fun `should return all failed when OpenRouter API key is blank`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterProperties.apiKey } returns ""

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual(null)
    expect(result.provider).toEqual(null)
    verify(exactly = 0) { openRouterVisionService.extractText(any()) }
  }

  @Test
  fun `should return no plate when all providers fail`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterVisionService.extractText(any()) } returns null

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual(null)
    expect(result.provider).toEqual(null)
  }

  @Test
  fun `should extract plate number from response with spaces`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterVisionService.extractText(any()) } returns "678 WKS"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("678WKS")
  }

  @Test
  fun `should handle lowercase plate response`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterVisionService.extractText(any()) } returns "123abc"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("123ABC")
  }

  @Test
  fun `should continue with other providers when one throws exception`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterVisionService.extractText(match { it.model == VisionModel.LLAMA_4_SCOUT.modelId }) } throws
      RuntimeException("API error")
    every { openRouterVisionService.extractText(match { it.model == VisionModel.NOVA_LITE.modelId }) } returns "333CCC"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual("333CCC")
  }

  @Test
  fun `should return null when response does not match plate pattern`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterVisionService.extractText(any()) } returns "No plate visible"

    val result = service.detectPlateNumber(base64Image, uuid)

    expect(result.plateNumber).toEqual(null)
    expect(result.provider).toEqual(null)
  }

  @Test
  fun `should run all OpenRouter models in parallel`() {
    val base64Image = "dGVzdA=="
    val uuid = UUID.randomUUID()
    every { openRouterVisionService.extractText(any()) } returns null

    service.detectPlateNumber(base64Image, uuid)

    verify(exactly = VisionModel.entries.size) { openRouterVisionService.extractText(any()) }
  }
}
