package ee.tenman.portfolio.auto24

import ee.tenman.portfolio.exception.CaptchaException
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class Auto24Service(
  private val auto24Client: Auto24Client,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000))
  fun findCarPrice(regNr: String): String {
    log.info("Fetching market price for registration number: {}", regNr)
    val response = auto24Client.getMarketPrice(regNr)
    return handleResponse(regNr, response)
  }

  private fun handleResponse(
    regNr: String,
    response: Auto24PriceResponse,
  ): String {
    if (response.error != null) {
      return handleError(regNr, response.error)
    }
    if (response.marketPrice == null) {
      log.warn("No price found for registration number: {}", regNr)
      return "Price not available"
    }
    log.info("Market price for {}: {} (attempt {}, duration {}s)", regNr, response.marketPrice, response.attempts, response.durationSeconds)
    return response.marketPrice
  }

  private fun handleError(
    regNr: String,
    error: String,
  ): String =
    when {
      error.contains("Vehicle not found") -> {
        log.info("Vehicle not found for registration number: {}", regNr)
        "Vehicle not found"
      }
      error.contains("Price not available") -> {
        log.info("Price not available for registration number: {}", regNr)
        "Price not available"
      }
      else -> {
        log.error("Failed to fetch price: {}", error)
        throw CaptchaException("Failed to fetch price: $error")
      }
    }
}
