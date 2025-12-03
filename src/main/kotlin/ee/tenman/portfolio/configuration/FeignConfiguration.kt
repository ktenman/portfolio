package ee.tenman.portfolio.configuration

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import feign.codec.Decoder
import feign.codec.Encoder
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConverters
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

@Configuration
class FeignConfiguration {
  @Bean
  @Primary
  fun jackson2ObjectMapper(): ObjectMapper =
    ObjectMapper()
      .registerModule(KotlinModule.Builder().build())
      .registerModule(JavaTimeModule())
      .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

  @Bean
  @Primary
  fun httpMessageConverters(jackson2ObjectMapper: ObjectMapper): HttpMessageConverters =
    HttpMessageConverters(MappingJackson2HttpMessageConverter(jackson2ObjectMapper))

  @Bean
  @Primary
  fun feignEncoder(jackson2ObjectMapper: ObjectMapper): Encoder = Jackson2FeignEncoder(jackson2ObjectMapper)

  @Bean
  @Primary
  fun feignDecoder(jackson2ObjectMapper: ObjectMapper): Decoder = Jackson2FeignDecoder(jackson2ObjectMapper)
}
