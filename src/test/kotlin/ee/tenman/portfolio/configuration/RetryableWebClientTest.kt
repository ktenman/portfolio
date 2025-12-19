package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.netty.channel.ConnectTimeoutException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.PrematureCloseException
import java.io.IOException
import java.nio.charset.StandardCharsets

class RetryableWebClientTest {
  private val testClient = TestRetryableWebClient()

  @Test
  fun `should identify ServiceUnavailable as retryable`() {
    val exception = createException(HttpStatus.SERVICE_UNAVAILABLE)
    expect(testClient.isRetryable(exception)).toEqual(true)
  }

  @Test
  fun `should identify GatewayTimeout as retryable`() {
    val exception = createException(HttpStatus.GATEWAY_TIMEOUT)
    expect(testClient.isRetryable(exception)).toEqual(true)
  }

  @Test
  fun `should identify TooManyRequests as retryable`() {
    val exception = createException(HttpStatus.TOO_MANY_REQUESTS)
    expect(testClient.isRetryable(exception)).toEqual(true)
  }

  @Test
  fun `should identify InternalServerError as retryable`() {
    val exception = createException(HttpStatus.INTERNAL_SERVER_ERROR)
    expect(testClient.isRetryable(exception)).toEqual(true)
  }

  @Test
  fun `should identify ConnectTimeoutException as retryable`() {
    val exception = ConnectTimeoutException("Connection timed out")
    expect(testClient.isRetryable(exception)).toEqual(true)
  }

  @Test
  fun `should identify PrematureCloseException as retryable`() {
    val exception = PrematureCloseException.TEST_EXCEPTION
    expect(testClient.isRetryable(exception)).toEqual(true)
  }

  @Test
  fun `should identify IOException as retryable`() {
    val exception = IOException("Connection reset")
    expect(testClient.isRetryable(exception)).toEqual(true)
  }

  @Test
  fun `should identify BadRequest as not retryable`() {
    val exception = createException(HttpStatus.BAD_REQUEST)
    expect(testClient.isRetryable(exception)).toEqual(false)
  }

  @Test
  fun `should identify NotFound as not retryable`() {
    val exception = createException(HttpStatus.NOT_FOUND)
    expect(testClient.isRetryable(exception)).toEqual(false)
  }

  @Test
  fun `should identify Unauthorized as not retryable`() {
    val exception = createException(HttpStatus.UNAUTHORIZED)
    expect(testClient.isRetryable(exception)).toEqual(false)
  }

  @Test
  fun `should identify generic RuntimeException as not retryable`() {
    val exception = RuntimeException("Some error")
    expect(testClient.isRetryable(exception)).toEqual(false)
  }

  @Test
  fun `should identify IllegalArgumentException as not retryable`() {
    val exception = IllegalArgumentException("Invalid argument")
    expect(testClient.isRetryable(exception)).toEqual(false)
  }

  private fun createException(status: HttpStatus): WebClientResponseException =
    WebClientResponseException.create(
      status.value(),
      status.reasonPhrase,
      HttpHeaders.EMPTY,
      ByteArray(0),
      StandardCharsets.UTF_8,
    )
}

private class TestRetryableWebClient : RetryableWebClient(WebClient.builder().build()) {
  public override fun isRetryable(throwable: Throwable): Boolean = super.isRetryable(throwable)
}
