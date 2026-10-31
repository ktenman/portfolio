package ee.tenman.portfolio.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfiguration {
  @Bean
  @Primary
  fun restClient(): RestClient = createClient(TIMEOUT_MS)

  @Bean
  fun logoSearchRestClient(): RestClient = createClient(LOGO_SEARCH_TIMEOUT_MS)

  private fun createClient(readTimeout: Int): RestClient =
    RestClient
      .builder()
      .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
      .requestFactory(
        SimpleClientHttpRequestFactory().apply {
          setConnectTimeout(TIMEOUT_MS)
          setReadTimeout(readTimeout)
        },
      ).build()

  companion object {
    private const val USER_AGENT = "Portfolio/1.0"
    private const val TIMEOUT_MS = 5000
    private const val LOGO_SEARCH_TIMEOUT_MS = 25000
  }
}
