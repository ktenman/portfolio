package ee.tenman.portfolio.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

@Configuration
class ObjectMapperConfig {
  @Bean
  fun jsonMapper(): JsonMapper = JsonMapperFactory.instance

  @Bean
  fun objectMapper(): ObjectMapper = JsonMapperFactory.instance
}
