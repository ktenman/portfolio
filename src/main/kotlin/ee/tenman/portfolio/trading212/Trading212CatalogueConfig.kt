package ee.tenman.portfolio.trading212

import feign.RequestInterceptor
import feign.auth.BasicAuthRequestInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean

class Trading212CatalogueConfig {
  @Bean
  fun basicAuthInterceptor(
    @Value("\${trading212.api.key-id:}") keyId: String,
    @Value("\${trading212.api.key-secret:}") keySecret: String,
  ): RequestInterceptor {
    if (keyId.isBlank() || keySecret.isBlank()) return RequestInterceptor { }
    return BasicAuthRequestInterceptor(keyId, keySecret)
  }
}
