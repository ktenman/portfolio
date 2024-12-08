package ee.tenman.portfolio.auto24

import ee.tenman.portfolio.configuration.TimeUtility
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CaptchaService(private val captchaClient: CaptchaClient) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun predict(predictionRequest: PredictionRequest): PredictionResponse {
    val startTime = System.currentTimeMillis()
    log.info("Starting CAPTCHA prediction")

    try {
      val response = captchaClient.predict(predictionRequest)

      log.info("CAPTCHA prediction successful. Prediction: ${response.prediction}, Confidence: ${response.confidence}")

      return response
    } catch (e: Exception) {
      log.error("Error during CAPTCHA prediction", e)
      throw RuntimeException("CAPTCHA prediction failed", e)
    } finally {
      val duration = TimeUtility.duration(startTime, TimeUtility.TimeUnit.MILLIS)
      log.info("CAPTCHA prediction completed in ${duration}ms")
    }
  }
}
