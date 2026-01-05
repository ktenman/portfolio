package ee.tenman.portfolio.service.infrastructure

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class ImageDownloadServiceTest {
  private val restClient = mockk<RestClient>()
  private val requestHeadersUriSpec = mockk<RestClient.RequestHeadersUriSpec<*>>()
  private val requestHeadersSpec = mockk<RestClient.RequestHeadersSpec<*>>()
  private val responseSpec = mockk<RestClient.ResponseSpec>()
  private lateinit var service: ImageDownloadService

  @BeforeEach
  fun setup() {
    service = ImageDownloadService(restClient)
  }

  @Nested
  inner class UrlValidation {
    @Test
    fun `should reject file scheme URLs`() {
      expect { service.download("file:///etc/passwd") }
        .toThrow<IllegalStateException>()
        .messageToContain("Invalid URL scheme")
    }

    @Test
    fun `should reject ftp scheme URLs`() {
      expect { service.download("ftp://example.com/image.png") }
        .toThrow<IllegalStateException>()
        .messageToContain("Invalid URL scheme")
    }

    @Test
    fun `should reject javascript scheme URLs`() {
      expect { service.download("javascript:alert(1)") }
        .toThrow<IllegalStateException>()
        .messageToContain("Invalid URL scheme")
    }

    @Test
    fun `should reject data scheme URLs`() {
      expect { service.download("data:image/png;base64,iVBOR") }
        .toThrow<IllegalStateException>()
        .messageToContain("Invalid URL scheme")
    }

    @Test
    fun `should accept https URLs`() {
      val imageData = ByteArray(100)
      setupMocksForUrl("https://example.com/image.png", imageData)

      val result = service.download("https://example.com/image.png")

      expect(result).toEqual(imageData)
    }

    @Test
    fun `should accept http URLs`() {
      val imageData = ByteArray(100)
      setupMocksForUrl("http://example.com/image.png", imageData)

      val result = service.download("http://example.com/image.png")

      expect(result).toEqual(imageData)
    }
  }

  @Nested
  inner class SizeLimit {
    @Test
    fun `should reject images larger than 2MB`() {
      val largeImage = ByteArray(2 * 1024 * 1024 + 1)
      setupMocksForUrl("https://example.com/large.png", largeImage)

      expect { service.download("https://example.com/large.png") }
        .toThrow<IllegalStateException>()
        .messageToContain("Image too large")
    }

    @Test
    fun `should accept images exactly 2MB`() {
      val maxSizeImage = ByteArray(2 * 1024 * 1024)
      setupMocksForUrl("https://example.com/max.png", maxSizeImage)

      val result = service.download("https://example.com/max.png")

      expect(result).toEqual(maxSizeImage)
    }

    @Test
    fun `should accept images smaller than 2MB`() {
      val smallImage = ByteArray(1024)
      setupMocksForUrl("https://example.com/small.png", smallImage)

      val result = service.download("https://example.com/small.png")

      expect(result).toEqual(smallImage)
    }
  }

  @Nested
  inner class Download {
    @Test
    fun `should throw when response is empty`() {
      setupMocksForUrl("https://example.com/empty.png", null)

      expect { service.download("https://example.com/empty.png") }
        .toThrow<IllegalStateException>()
        .messageToContain("Empty response")
    }
  }

  private fun setupMocksForUrl(url: String, responseBody: ByteArray?) {
    every { restClient.get() } returns requestHeadersUriSpec
    every { requestHeadersUriSpec.uri(url) } returns requestHeadersSpec
    every { requestHeadersSpec.retrieve() } returns responseSpec
    every { responseSpec.body(ByteArray::class.java) } returns responseBody
  }
}
