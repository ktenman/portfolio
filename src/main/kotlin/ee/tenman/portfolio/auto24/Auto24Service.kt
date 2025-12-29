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
  fun findCarPrice(regNr: String): CarPriceResult {
    log.info("Fetching market price for registration number: $regNr")
    val response = auto24Client.getMarketPrice(regNr)
    return handleResponse(regNr, response)
  }

  private fun handleResponse(
    regNr: String,
    response: Auto24PriceResponse,
  ): CarPriceResult {
    if (response.error != null) return handleError(regNr, response.error, response.durationSeconds)
    if (response.marketPrice == null) {
      log.warn("No price found for registration number: $regNr")
      return CarPriceResult(price = null, error = "Price not available", durationSeconds = response.durationSeconds)
    }
    log.info("Market price for $regNr: ${response.marketPrice} (attempt ${response.attempts}, duration ${response.durationSeconds}s)")
    return CarPriceResult(price = response.marketPrice, durationSeconds = response.durationSeconds)
  }

  private fun handleError(
    regNr: String,
    error: String,
    durationSeconds: Double?,
  ): CarPriceResult =
    when {
      error.contains("Vehicle not found") -> {
        log.info("Vehicle not found for registration number: $regNr")
        CarPriceResult(price = null, error = "Vehicle not found", durationSeconds = durationSeconds)
      }
      error.contains("Price not available") -> {
        log.info("Price not available for registration number: $regNr")
        CarPriceResult(price = null, error = "Price not available", durationSeconds = durationSeconds)
      }
      else -> {
        log.error("Failed to fetch price: $error")
        throw CaptchaException("Failed to fetch price: $error")
      }
    }
}
