package ee.tenman.portfolio.configuration

import feign.codec.Decoder
import feign.codec.Encoder
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

  @Bean
  fun feignEncoder(): Encoder = JacksonFeignEncoder(JsonMapperFactory.instance)

  @Bean
  fun feignDecoder(): Decoder = JacksonFeignDecoder(JsonMapperFactory.instance)
}
