package ee.tenman.portfolio.auto24

import ee.tenman.portfolio.configuration.RetryableWebClient
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class Auto24WebClient(
  proxyWebClient: WebClient,
) : RetryableWebClient(proxyWebClient) {
  suspend fun getMarketPrice(regNumber: String): Auto24PriceResponse =
    webClient
      .get()
      .uri("/auto24/price?regNumber={regNumber}", regNumber)
      .retrieve()
      .bodyToMono(Auto24PriceResponse::class.java)
      .withRetry("Auto24[$regNumber]")
}
