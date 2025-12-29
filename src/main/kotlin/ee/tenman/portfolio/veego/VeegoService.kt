package ee.tenman.portfolio.veego

import ee.tenman.portfolio.configuration.TimeUtility
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class VeegoService(
  private val veegoClient: VeegoClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000))
  fun getTaxInfo(plateNumber: String): VeegoResult {
    log.info("Fetching tax info for plate: $plateNumber")
    val startTime = System.nanoTime()
    return runCatching {
      val response = veegoClient.getTaxInfo(plateNumber, VeegoTaxRequest(plateNumber))
      val duration = TimeUtility.durationInSeconds(startTime).toDouble()
      log.info("Tax info retrieved for $plateNumber: ${response.annualTax} EUR annual, ${response.registrationTax} EUR registration")
      VeegoResult.fromResponse(response, duration)
    }.getOrElse { exception ->
      val duration = TimeUtility.durationInSeconds(startTime).toDouble()
      log.error("Failed to fetch tax info for $plateNumber: ${exception.message}")
      VeegoResult.error(exception.message ?: "Unknown error", duration)
    }
  }
}
