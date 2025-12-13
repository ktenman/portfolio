package ee.tenman.portfolio.auto24

import ee.tenman.portfolio.exception.CaptchaException
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class Auto24Service(
  private val auto24ProxyClient: Auto24ProxyClient,
  private val captchaService: CaptchaService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val MAX_ATTEMPTS = 10
    private const val SUCCESS_STATUS = "success"
  }

  @Retryable(backoff = Backoff(delay = 1000))
  fun findCarPrice(regNr: String): String {
    repeat(MAX_ATTEMPTS) { attempt ->
      log.info("Attempt {} of {} for {}", attempt + 1, MAX_ATTEMPTS, regNr)
      val result = processCaptchaAttempt(regNr)
      if (result != null) return result
      log.warn("CAPTCHA failed, retrying...")
    }
    throw CaptchaException("Failed to solve CAPTCHA after $MAX_ATTEMPTS attempts")
  }

  private fun processCaptchaAttempt(regNr: String): String? {
    val captchaResponse = auto24ProxyClient.getCaptcha(CaptchaRequest(regNr))
    if (captchaResponse.status == SUCCESS_STATUS) {
      return requirePrice(captchaResponse.price)
    }
    val (sessionId, captchaImage) = extractCaptchaDetails(captchaResponse)
    val prediction = captchaService.predict(PredictionRequest(UUID.randomUUID(), captchaImage))
    log.info("CAPTCHA prediction: {} (confidence: {})", prediction.prediction, prediction.confidence)
    val submitResponse = auto24ProxyClient.submitCaptcha(SubmitRequest(sessionId, prediction.prediction))
    if (submitResponse.status == SUCCESS_STATUS) {
      return requirePrice(submitResponse.price)
    }
    return null
  }

  private fun extractCaptchaDetails(response: CaptchaResponse): Pair<String, String> {
    val sessionId = response.sessionId ?: throw CaptchaException("No session ID returned")
    val captchaImage = response.captchaImage ?: throw CaptchaException("No CAPTCHA image returned")
    return Pair(sessionId, captchaImage)
  }

  private fun requirePrice(price: String?): String = price ?: throw CaptchaException("Price not found in response")
}
