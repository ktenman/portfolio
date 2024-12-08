package ee.tenman.portfolio.auto24

import ee.tenman.portfolio.configuration.TimeUtility
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class CaptchaService(private val captchaClient: CaptchaClient) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000))
  fun predict(predictionRequest: PredictionRequest): PredictionResponse {
    val startTime = System.currentTimeMillis()
    log.info("Starting CAPTCHA prediction")

    try {
      log.info("Predicting CAPTCHA with UUID: ${predictionRequest.uuid}")
      val response = captchaClient.predict(predictionRequest)

      log.info("CAPTCHA prediction successful. Prediction: ${response.prediction}, Confidence: ${response.confidence}")

      return response
    } catch (e: Exception) {
      log.error("Error during CAPTCHA prediction", e)
      throw RuntimeException("CAPTCHA prediction failed", e)
    } finally {
      val duration = TimeUtility.duration(startTime, TimeUnit.MILLISECONDS)
      log.info("CAPTCHA prediction completed in ${duration}ms")
    }
  }
}
