package ee.tenman.portfolio.service.monitoring

import com.fasterxml.jackson.core.JsonProcessingException
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionRunResult
import feign.FeignException
import io.micrometer.core.instrument.MeterRegistry
import jakarta.persistence.PersistenceException
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.TransactionException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientResponseException
import tools.jackson.core.JacksonException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.sql.SQLException
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeoutException

@Service
class CollectionMonitorService(
  private val stateService: CollectionStateService,
  private val registry: MeterRegistry,
) {
  companion object {
    private val locks = CollectionKey.entries.associateWith { Any() }
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  fun <T> collect(
    key: CollectionKey,
    symbols: Collection<String>,
    action: (CollectionRun) -> T,
  ): T =
    synchronized(locks.getValue(key)) {
    val expected = symbols.toSet()
    val started =
      runCatching {
      stateService.initialize(key, expected)
      stateService.begin(key)
    }.getOrElse {
      recordStorageFailure(key, it)
      throw it
    }
    val run = CollectionRun(expected) { stateService.recordPersistence(key, it) }
    val outcome = runCatching { action(run) }
    val result = run.result()
    val storage = runCatching { stateService.finish(key, started, result, outcome.isSuccess) }.exceptionOrNull()
    storage?.let { recordStorageFailure(key, it) }
    val metrics = runCatching { recordFailures(key, result, outcome.exceptionOrNull()) }.exceptionOrNull()
    val original = outcome.exceptionOrNull()
    if (original != null) {
      storage?.let(original::addSuppressed)
      metrics?.let(original::addSuppressed)
      throw original
    }
    if (storage != null) {
      metrics?.let(storage::addSuppressed)
      throw storage
    }
    if (metrics != null) throw metrics
    outcome.getOrThrow()
  }

  private fun recordStorageFailure(
    key: CollectionKey,
    error: Throwable,
  ) {
    runCatching {
      incrementFailure(key, "persistence", "none")
    }.exceptionOrNull()?.let(error::addSuppressed)
  }

  private fun recordFailures(
    key: CollectionKey,
    result: CollectionRunResult,
    actionError: Throwable?,
  ) {
    result.failed.forEach { symbol ->
      val error = result.failures[symbol] ?: actionError
      val (category, status) = classify(error)
      incrementFailure(key, category, status)
    }
    if (result.failed.isEmpty() && actionError != null) {
      val (category, status) = classify(actionError)
      incrementFailure(key, category, status)
    }
  }

  private fun incrementFailure(
    key: CollectionKey,
    category: String,
    status: String,
  ) {
    registry
      .counter(
      "portfolio.collection.failures",
      "provider",
      key.provider,
      "operation",
      key.operation,
      "category",
      category,
      "status",
      status,
    ).increment()
  }
}

internal fun classify(error: Throwable?): Pair<String, String> {
  val causes = generateSequence(error) { it.cause }.take(8).toList()
  if (causes.isEmpty()) return "invalid_response" to "none"
  val status =
    causes.firstNotNullOfOrNull {
    when (it) {
      is FeignException -> it.status()
      is RestClientResponseException -> it.statusCode.value()
      else -> null
    }
  }
  if (status != null && status in 400..599) return classifyStatus(status)
  val category =
    when {
    causes.any {
      it is DataAccessException || it is SQLException || it is PersistenceException || it is TransactionException
    } -> "persistence"
    causes.any { it is SocketTimeoutException || it is HttpTimeoutException || it is TimeoutException } -> "timeout"
    causes.any {
      it is JsonProcessingException ||
        it is JacksonException ||
        it is NumberFormatException ||
        it is DateTimeParseException ||
        it is IllegalArgumentException
    } -> "invalid_response"
    else -> "unknown"
  }
  return category to "none"
}

private fun classifyStatus(status: Int): Pair<String, String> =
  when (status) {
    401, 403 -> "unauthorized" to status.toString()
    429 -> "rate_limit" to "429"
    404 -> "http_4xx" to "404"
    in 400..499 -> "http_4xx" to "4xx"
    in 500..599 -> "http_5xx" to "5xx"
    else -> "unknown" to "none"
  }
