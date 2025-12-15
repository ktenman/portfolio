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
    if (response.error != null) {
      if (response.error.contains("Vehicle not found")) {
        log.info("Vehicle not found for registration number: {}", regNr)
        return "Vehicle not found"
      }
      log.error("Failed to fetch price: {}", response.error)
      throw CaptchaException("Failed to fetch price: ${response.error}")
    }
    if (response.marketPrice == null) {
      log.warn("No price found for registration number: {}", regNr)
      return "Price not found"
    }
    log.info("Market price for {}: {} (found on attempt {})", regNr, response.marketPrice, response.attempts)
    return response.marketPrice
  }
}
