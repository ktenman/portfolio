package ee.tenman.portfolio.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class ObjectMapperConfig {
  @Bean
  fun objectMapper(): ObjectMapper = JsonMapperFactory.instance
}
