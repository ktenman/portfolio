package ee.tenman.portfolio.googlevision

import feign.RequestInterceptor
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
  name = GoogleVisionClient.CLIENT_NAME,
  url = GoogleVisionClient.CLIENT_URL,
  configuration = [GoogleVisionClient.Configuration::class],
)
interface GoogleVisionClient {
  companion object {
    const val CLIENT_NAME = "googleVisionClient"
    const val CLIENT_URL = "https://vision.googleapis.com/v1"
  }

  @PostMapping("/images:annotate")
  fun analyzeImage(
    @RequestBody requestBody: GoogleVisionApiRequest,
  ): GoogleVisionApiResponse

  class Configuration {
    @Bean
    fun requestInterceptor(authService: VisionAuthenticatorService): RequestInterceptor =
      RequestInterceptor { requestTemplate ->
        requestTemplate.header("Authorization", "Bearer ${authService.accessToken}")
        requestTemplate.header("Content-Type", "application/json")
        // Add x-goog-api-client header for better tracking
        requestTemplate.header("x-goog-api-client", "feign-kotlin")
      }
  }
}
