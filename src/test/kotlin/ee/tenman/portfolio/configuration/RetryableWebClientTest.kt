package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.netty.channel.ConnectTimeoutException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.PrematureCloseException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.stream.Stream

class RetryableWebClientTest {
  private val testClient = TestRetryableWebClient()

  @ParameterizedTest(name = "should identify {0} as retryable")
  @MethodSource("retryableExceptions")
  fun `should identify exception as retryable`(
    @Suppress("UNUSED_PARAMETER") _name: String,
    exception: Throwable,
  ) {
    expect(testClient.isRetryable(exception)).toEqual(true)
  }

  @ParameterizedTest(name = "should identify {0} as not retryable")
  @MethodSource("nonRetryableExceptions")
  fun `should identify exception as not retryable`(
    @Suppress("UNUSED_PARAMETER") _name: String,
    exception: Throwable,
  ) {
    expect(testClient.isRetryable(exception)).toEqual(false)
  }

  companion object {
    @JvmStatic
    fun retryableExceptions(): Stream<Arguments> =
      Stream.of(
        Arguments.of("ServiceUnavailable", createException(HttpStatus.SERVICE_UNAVAILABLE)),
        Arguments.of("GatewayTimeout", createException(HttpStatus.GATEWAY_TIMEOUT)),
        Arguments.of("TooManyRequests", createException(HttpStatus.TOO_MANY_REQUESTS)),
        Arguments.of("InternalServerError", createException(HttpStatus.INTERNAL_SERVER_ERROR)),
        Arguments.of("ConnectTimeoutException", ConnectTimeoutException("Connection timed out")),
        Arguments.of("PrematureCloseException", PrematureCloseException.TEST_EXCEPTION),
        Arguments.of("IOException", IOException("Connection reset")),
      )

    @JvmStatic
    fun nonRetryableExceptions(): Stream<Arguments> =
      Stream.of(
        Arguments.of("BadRequest", createException(HttpStatus.BAD_REQUEST)),
        Arguments.of("NotFound", createException(HttpStatus.NOT_FOUND)),
        Arguments.of("Unauthorized", createException(HttpStatus.UNAUTHORIZED)),
        Arguments.of("RuntimeException", RuntimeException("Some error")),
        Arguments.of("IllegalArgumentException", IllegalArgumentException("Invalid argument")),
      )

    private fun createException(status: HttpStatus): WebClientResponseException =
      WebClientResponseException.create(
        status.value(),
        status.reasonPhrase,
        HttpHeaders.EMPTY,
        ByteArray(0),
        StandardCharsets.UTF_8,
      )
  }
}

private class TestRetryableWebClient : RetryableWebClient(WebClient.builder().build()) {
  public override fun isRetryable(throwable: Throwable): Boolean = super.isRetryable(throwable)
}
