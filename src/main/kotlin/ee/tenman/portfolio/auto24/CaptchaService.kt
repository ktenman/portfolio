package ee.tenman.portfolio.auto24

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CaptchaService(
  private val captchaClient: CaptchaClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun predict(predictionRequest: PredictionRequest): PredictionResponse {
    log.info("Predicting CAPTCHA with UUID: ${predictionRequest.uuid}")
    val response = captchaClient.predict(predictionRequest)
    log.info("CAPTCHA prediction successful. Prediction: ${response.prediction}, Confidence: ${response.confidence}")
    return response
  }
}
