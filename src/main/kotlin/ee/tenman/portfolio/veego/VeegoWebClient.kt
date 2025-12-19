package ee.tenman.portfolio.veego

import ee.tenman.portfolio.configuration.RetryableWebClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class VeegoWebClient(
  directApiWebClient: WebClient,
  @Value("\${veego.url:https://api.veego.ee/api}") private val baseUrl: String,
) : RetryableWebClient(directApiWebClient) {
  suspend fun getTaxInfo(plateNumber: String): VeegoTaxResponse =
    webClient
      .post()
      .uri("$baseUrl/vehicles/{plate}/tax", plateNumber)
      .bodyValue(VeegoTaxRequest(plateNumber))
      .retrieve()
      .bodyToMono(VeegoTaxResponse::class.java)
      .withRetry("Veego[$plateNumber]")
}
