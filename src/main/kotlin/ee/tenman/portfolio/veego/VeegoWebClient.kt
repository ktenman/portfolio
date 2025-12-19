package ee.tenman.portfolio.veego

import ee.tenman.portfolio.configuration.RetryableWebClient
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class VeegoWebClient(
  veegoApiWebClient: WebClient,
) : RetryableWebClient(veegoApiWebClient) {
  suspend fun getTaxInfo(plateNumber: String): VeegoTaxResponse =
    webClient
      .post()
      .uri("/vehicles/{plate}/tax", plateNumber)
      .bodyValue(VeegoTaxRequest(plateNumber))
      .retrieve()
      .bodyToMono(VeegoTaxResponse::class.java)
      .withRetry("Veego[$plateNumber]")
}
