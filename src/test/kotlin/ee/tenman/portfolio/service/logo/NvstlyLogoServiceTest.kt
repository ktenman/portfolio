package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.RequestHeadersUriSpec
import org.springframework.web.client.RestClient.ResponseSpec

class NvstlyLogoServiceTest {
  private val restClient = mockk<RestClient>()
  private val requestHeadersUriSpec = mockk<RequestHeadersUriSpec<*>>()
  private val responseSpec = mockk<ResponseSpec>()
  private lateinit var service: NvstlyLogoService

  @BeforeEach
  fun setUp() {
    service = NvstlyLogoService(restClient)
  }

  @Test
  fun `should return null when ticker is blank`() {
    val result = service.fetchLogo("   ")

    expect(result).toEqual(null)
    verify(exactly = 0) { restClient.get() }
  }

  @Test
  fun `should return null when fetch fails`() {
    every { restClient.get() } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.retrieve() } returns responseSpec
    every { responseSpec.body(ByteArray::class.java) } throws RuntimeException("Not found")

    val result = service.fetchLogo("INVALID")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return logo data when fetch succeeds`() {
    val logoData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { restClient.get() } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.retrieve() } returns responseSpec
    every { responseSpec.body(ByteArray::class.java) } returns logoData

    val result = service.fetchLogo("AAPL")

    expect(result).toEqual(logoData)
  }

  @Test
  fun `should uppercase ticker in URL`() {
    val logoData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { restClient.get() } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.retrieve() } returns responseSpec
    every { responseSpec.body(ByteArray::class.java) } returns logoData

    service.fetchLogo("aapl")

    verify {
      requestHeadersUriSpec.uri("https://raw.githubusercontent.com/nvstly/icons/main/ticker_icons/AAPL.png")
    }
  }
}
