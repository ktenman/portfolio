package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.cloudflarebypassproxy.CloudflareBypassProxyProperties
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.RequestBodySpec
import org.springframework.web.client.RestClient.RequestBodyUriSpec
import org.springframework.web.client.RestClient.ResponseSpec

class ImageSearchLogoServiceTest {
  private val restClient = mockk<RestClient>()
  private val requestBodyUriSpec = mockk<RequestBodyUriSpec>()
  private val requestBodySpec = mockk<RequestBodySpec>()
  private val responseSpec = mockk<ResponseSpec>()
  private val cloudflareBypassProxyProperties = mockk<CloudflareBypassProxyProperties>()
  private val imageDownloadService = mockk<ImageDownloadService>()
  private lateinit var service: ImageSearchLogoService

  @BeforeEach
  fun setUp() {
    every { cloudflareBypassProxyProperties.url } returns "http://localhost:3000"
    service = ImageSearchLogoService(restClient, cloudflareBypassProxyProperties, imageDownloadService)
  }

  @Test
  fun `should return empty list when company name is blank`() {
    val result = service.searchLogoUrls("   ")

    expect(result).toHaveSize(0)
    verify(exactly = 0) { restClient.post() }
  }

  @Test
  fun `should return Bing results when available`() {
    val bingResponse =
      ImageSearchResponse(
        success = true,
        results =
          listOf(
            ImageResult(image = "https://example.com/logo1.png", thumbnail = "", title = "", width = 100, height = 100),
            ImageResult(image = "https://example.com/logo2.png", thumbnail = "", title = "", width = 100, height = 100),
          ),
      )
    setupRestClientMock(bingResponse)

    val result = service.searchLogoUrls("Apple Inc")

    expect(result).toContainExactly("https://example.com/logo1.png", "https://example.com/logo2.png")
  }

  @Test
  fun `should return empty list when Bing fails`() {
    val failedResponse = ImageSearchResponse(success = false, results = emptyList())
    setupRestClientMock(failedResponse)

    val result = service.searchLogoUrls("Apple Inc")

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should download first valid image from Bing`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    val bingResponse =
      ImageSearchResponse(
        success = true,
        results =
          listOf(
            ImageResult(image = "https://example.com/logo.png", thumbnail = "", title = "", width = 100, height = 100),
          ),
      )
    setupRestClientMock(bingResponse)
    every { imageDownloadService.downloadOrNull("https://example.com/logo.png") } returns imageData

    val result = service.searchAndDownloadLogo("Apple Inc")

    expect(result?.imageData).toEqual(imageData)
    expect(result?.source).toEqual(LogoSource.BING)
  }

  @Test
  fun `should try next URL when download fails`() {
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    val bingResponse =
      ImageSearchResponse(
        success = true,
        results =
          listOf(
            ImageResult(image = "https://example.com/bad.png", thumbnail = "", title = "", width = 100, height = 100),
            ImageResult(image = "https://example.com/good.png", thumbnail = "", title = "", width = 100, height = 100),
          ),
      )
    setupRestClientMock(bingResponse)
    every { imageDownloadService.downloadOrNull("https://example.com/bad.png") } returns null
    every { imageDownloadService.downloadOrNull("https://example.com/good.png") } returns imageData

    val result = service.searchAndDownloadLogo("Apple Inc")

    expect(result?.imageData).toEqual(imageData)
    expect(result?.source).toEqual(LogoSource.BING)
  }

  @Test
  fun `should return null when no images can be downloaded`() {
    val bingResponse =
      ImageSearchResponse(
        success = true,
        results =
          listOf(
            ImageResult(image = "https://example.com/bad.png", thumbnail = "", title = "", width = 100, height = 100),
          ),
      )
    setupRestClientMock(bingResponse)
    every { imageDownloadService.downloadOrNull(any()) } returns null

    val result = service.searchAndDownloadLogo("Apple Inc")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return null when company name is blank for download`() {
    val result = service.searchAndDownloadLogo("   ")

    expect(result).toEqual(null)
    verify(exactly = 0) { restClient.post() }
  }

  private fun setupRestClientMock(response: ImageSearchResponse) {
    every { restClient.post() } returns requestBodyUriSpec
    every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
    every { requestBodySpec.contentType(MediaType.APPLICATION_JSON) } returns requestBodySpec
    every { requestBodySpec.body(any<ImageSearchRequest>()) } returns requestBodySpec
    every { requestBodySpec.retrieve() } returns responseSpec
    every { responseSpec.body(ImageSearchResponse::class.java) } returns response
  }
}
