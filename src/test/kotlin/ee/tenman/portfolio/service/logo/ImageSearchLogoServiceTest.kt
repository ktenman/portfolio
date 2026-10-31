package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import ee.tenman.portfolio.cloudflarebypassproxy.CloudflareBypassProxyProperties
import ee.tenman.portfolio.configuration.RestClientConfiguration
import ee.tenman.portfolio.exception.LogoSearchUnavailableException
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
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
  private lateinit var service: ImageSearchLogoService

  @BeforeEach
  fun setUp() {
    every { cloudflareBypassProxyProperties.url } returns "http://localhost:3000"
    service = ImageSearchLogoService(restClient, cloudflareBypassProxyProperties)
  }

  @Test
  fun `should not search for a blank company name`() {
    val result = service.searchLogoCandidates("   ")

    expect(result).toHaveSize(0)
    verify(exactly = 0) { restClient.post() }
  }

  @Test
  fun `should preserve DuckDuckGo candidate metadata and requested result limit`() {
    val response =
      ImageSearchResponse(
        success = true,
        results = listOf(ImageResult("https://example.com/logo.png", "https://example.com/thumb.png", "Äri logo")),
      )
    setupRestClientMock(response, "Äri AS", 3)
    val result = service.searchLogoCandidates("Äri AS", 3)
    expect(result).toContainExactly(
      LogoCandidate(
        "${IMAGE_PROXY}https%3A%2F%2Fexample.com%2Flogo.png",
        "${IMAGE_PROXY}https%3A%2F%2Fexample.com%2Fthumb.png",
        "Äri logo",
        0,
      ),
    )
  }

  @Test
  fun `should encode private image URLs through the proxy with a trailing slash`() {
    every { cloudflareBypassProxyProperties.url } returns "http://localhost:3000/"
    val response =
      ImageSearchResponse(
        success = true,
        results =
          listOf(
            ImageResult(
              image = "http://127.0.0.1/logo png?token=a+b&next=c?d=e",
              thumbnail = "http://169.254.169.254/latest/meta-data/",
              title = "Äri logo",
            ),
          ),
      )
    setupRestClientMock(response)
    val result = service.searchLogoCandidates("Apple Inc")
    expect(result).toContainExactly(
      LogoCandidate(
        "${IMAGE_PROXY}http%3A%2F%2F127.0.0.1%2Flogo+png%3Ftoken%3Da%2Bb%26next%3Dc%3Fd%3De",
        "${IMAGE_PROXY}http%3A%2F%2F169.254.169.254%2Flatest%2Fmeta-data%2F",
        "Äri logo",
        0,
      ),
    )
  }

  @Test
  fun `should throw when DuckDuckGo reports failure`() {
    setupRestClientMock(ImageSearchResponse(success = false, error = "DuckDuckGo request was blocked"))
    expect { service.searchLogoCandidates("Apple Inc") }.toThrow<LogoSearchUnavailableException>()
  }

  @Test
  fun `should throw when the search request fails`() {
    setupRestClientMock(ImageSearchResponse(success = true))
    every { responseSpec.body(ImageSearchResponse::class.java) } throws IllegalStateException("Proxy unavailable")
    expect { service.searchLogoCandidates("Apple Inc") }.toThrow<LogoSearchUnavailableException>()
  }

  @Test
  fun `should throw when the proxy answers with no body`() {
    setupRestClientMock(ImageSearchResponse(success = true))
    every { responseSpec.body(ImageSearchResponse::class.java) } returns null
    expect { service.searchLogoCandidates("Apple Inc") }.toThrow<LogoSearchUnavailableException>()
  }

  @Test
  fun `should return an empty list when an answered search has no results`() {
    setupRestClientMock(ImageSearchResponse(success = true))
    val result = service.searchLogoCandidates("Apple Inc")
    expect(result).toHaveSize(0)
  }

  @Nested
  inner class ProtectedDownloads {
    @RegisterExtension
    val proxy: WireMockExtension = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build()

    private val downloads = ImageDownloadService(RestClientConfiguration().restClient())

    @Test
    fun `should use the guarded proxy for private candidate images and thumbnails`() {
      val image = "${proxy.baseUrl()}/private-logo.png?token=a+b&next=c?d=e"
      val thumbnail = "${proxy.baseUrl()}/private-thumbnail.png"
      reject(image)
      reject(thumbnail)
      every { cloudflareBypassProxyProperties.url } returns proxy.baseUrl()
      val response = ImageSearchResponse(success = true, results = listOf(ImageResult(image, thumbnail, "Äri logo")))
      setupRestClientMock(response, baseUrl = proxy.baseUrl())
      val candidate = service.searchLogoCandidates("Apple Inc").single()
      val images = listOf(downloads.downloadOrNull(candidate.imageUrl), downloads.downloadOrNull(candidate.thumbnailUrl))
      expect(images).toContainExactly(null, null)
      proxy.verify(1, getRequestedFor(urlPathEqualTo("/duckduckgo/image")).withQueryParam("url", equalTo(image)))
      proxy.verify(1, getRequestedFor(urlPathEqualTo("/duckduckgo/image")).withQueryParam("url", equalTo(thumbnail)))
      proxy.verify(0, getRequestedFor(urlPathEqualTo("/private-logo.png")))
      proxy.verify(0, getRequestedFor(urlPathEqualTo("/private-thumbnail.png")))
    }

    private fun reject(url: String) {
      proxy.stubFor(
        get(urlPathEqualTo("/duckduckgo/image"))
          .withQueryParam("url", equalTo(url))
          .willReturn(aResponse().withStatus(400)),
      )
    }
  }

  private fun setupRestClientMock(
    response: ImageSearchResponse,
    query: String = "Apple Inc",
    maxResults: Int = 10,
    baseUrl: String = "http://localhost:3000",
  ) {
    every { restClient.post() } returns requestBodyUriSpec
    every { requestBodyUriSpec.uri("$baseUrl/duckduckgo/images") } returns requestBodySpec
    every { requestBodySpec.contentType(MediaType.APPLICATION_JSON) } returns requestBodySpec
    every { requestBodySpec.body(ImageSearchRequest(query, maxResults)) } returns requestBodySpec
    every { requestBodySpec.retrieve() } returns responseSpec
    every { responseSpec.body(ImageSearchResponse::class.java) } returns response
  }

  companion object {
    private const val IMAGE_PROXY = "http://localhost:3000/duckduckgo/image?url="
  }
}
