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
  fun `should return null when symbol is blank`() {
    val result = service.fetchCryptoLogo("   ")

    expect(result).toEqual(null)
    verify(exactly = 0) { restClient.get() }
  }

  @Test
  fun `should return null when fetch fails`() {
    every { restClient.get() } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.retrieve() } returns responseSpec
    every { responseSpec.body(ByteArray::class.java) } throws RuntimeException("Not found")

    val result = service.fetchCryptoLogo("INVALID")

    expect(result).toEqual(null)
  }

  @Test
  fun `should return logo data when fetch succeeds`() {
    val logoData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    every { restClient.get() } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.retrieve() } returns responseSpec
    every { responseSpec.body(ByteArray::class.java) } returns logoData

    val result = service.fetchCryptoLogo("BTC")

    expect(result).toEqual(logoData)
  }

  @Test
  fun `fetches a crypto icon from the crypto icons folder`() {
    every { restClient.get() } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.retrieve() } returns responseSpec
    every { responseSpec.body(ByteArray::class.java) } returns byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    service.fetchCryptoLogo("btc")

    verify {
      requestHeadersUriSpec.uri("https://raw.githubusercontent.com/nvstly/icons/main/crypto_icons/BTC.png")
    }
  }
}
