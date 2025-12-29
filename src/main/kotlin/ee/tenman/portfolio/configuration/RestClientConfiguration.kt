package ee.tenman.portfolio.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfiguration {
  @Bean
  fun restClient(): RestClient =
    RestClient
      .builder()
      .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
      .requestFactory(
        SimpleClientHttpRequestFactory().apply {
          setConnectTimeout(TIMEOUT_MS)
          setReadTimeout(TIMEOUT_MS)
        },
      ).build()

  companion object {
    private const val USER_AGENT = "Portfolio/1.0"
    private const val TIMEOUT_MS = 5000
  }
}
