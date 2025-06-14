package ee.tenman.portfolio.auto24

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
  name = CaptchaClient.CLIENT_NAME,
  url = "\${captcha.url}",
)
interface CaptchaClient {
  companion object {
    const val CLIENT_NAME = "captchaClient"
  }

  @PostMapping("/predict")
  fun predict(
    @RequestBody request: PredictionRequest,
  ): PredictionResponse
}
