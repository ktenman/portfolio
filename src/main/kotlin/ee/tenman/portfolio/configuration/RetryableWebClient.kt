package ee.tenman.portfolio.configuration

import io.netty.channel.ConnectTimeoutException
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.netty.http.client.PrematureCloseException
import reactor.util.retry.Retry
import java.io.IOException
import java.time.Duration

abstract class RetryableWebClient(
  protected val webClient: WebClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  protected suspend fun <T : Any> Mono<T>.withRetry(context: String): T =
    this
      .retryWhen(
        Retry
          .backoff(MAX_RETRIES, Duration.ofMillis(INITIAL_DELAY_MS))
          .maxBackoff(Duration.ofMillis(MAX_DELAY_MS))
          .jitter(JITTER_FACTOR)
          .filter { isRetryable(it) }
          .doBeforeRetry { log.warn("Retrying {} request, attempt {}", context, it.totalRetries() + 1) },
      ).awaitSingle()

  protected fun isRetryable(throwable: Throwable): Boolean =
    when (throwable) {
      is WebClientResponseException.ServiceUnavailable -> true
      is WebClientResponseException.GatewayTimeout -> true
      is WebClientResponseException.TooManyRequests -> true
      is WebClientResponseException.InternalServerError -> true
      is ConnectTimeoutException -> true
      is PrematureCloseException -> true
      is IOException -> true
      else -> false
    }

  companion object {
    private const val MAX_RETRIES = 3L
    private const val INITIAL_DELAY_MS = 200L
    private const val MAX_DELAY_MS = 2000L
    private const val JITTER_FACTOR = 0.5
  }
}
