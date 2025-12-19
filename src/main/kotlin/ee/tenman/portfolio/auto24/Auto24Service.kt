package ee.tenman.portfolio.auto24

import ee.tenman.portfolio.exception.CaptchaException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class Auto24Service(
  private val auto24WebClient: Auto24WebClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  suspend fun findCarPrice(regNr: String): CarPriceResult {
    log.info("Fetching market price for registration number: {}", regNr)
    val response = auto24WebClient.getMarketPrice(regNr)
    return handleResponse(regNr, response)
  }

  private fun handleResponse(
    regNr: String,
    response: Auto24PriceResponse,
  ): CarPriceResult {
    if (response.error != null) return handleError(regNr, response.error, response.durationSeconds)
    if (response.marketPrice == null) {
      log.warn("No price found for registration number: {}", regNr)
      return CarPriceResult(price = null, error = "Price not available", durationSeconds = response.durationSeconds)
    }
    log.info(
      "Market price for {}: {} (attempt {}, duration {}s)",
      regNr,
      response.marketPrice,
      response.attempts,
      response.durationSeconds,
    )
    return CarPriceResult(price = response.marketPrice, durationSeconds = response.durationSeconds)
  }

  private fun handleError(
    regNr: String,
    error: String,
    durationSeconds: Double?,
  ): CarPriceResult =
    when {
      error.contains("Vehicle not found") -> {
        log.info("Vehicle not found for registration number: {}", regNr)
        CarPriceResult(price = null, error = "Vehicle not found", durationSeconds = durationSeconds)
      }
      error.contains("Price not available") -> {
        log.info("Price not available for registration number: {}", regNr)
        CarPriceResult(price = null, error = "Price not available", durationSeconds = durationSeconds)
      }
      else -> {
        log.error("Failed to fetch price: {}", error)
        throw CaptchaException("Failed to fetch price: $error")
      }
    }
}
