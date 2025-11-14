package ee.tenman.portfolio.openrouter

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import feign.Logger
import feign.codec.Decoder
import feign.codec.Encoder
import org.springframework.beans.factory.ObjectFactory
import org.springframework.boot.autoconfigure.http.HttpMessageConverters
import org.springframework.cloud.openfeign.support.SpringDecoder
import org.springframework.cloud.openfeign.support.SpringEncoder
import org.springframework.context.annotation.Bean
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

class OpenRouterFeignConfiguration {
  @Bean
  fun feignLoggerLevel(): Logger.Level = Logger.Level.FULL

  @Bean
  fun feignEncoder(): Encoder {
    val objectMapper =
      ObjectMapper().apply {
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      }

    val converter = MappingJackson2HttpMessageConverter(objectMapper)
    val messageConverters = ObjectFactory { HttpMessageConverters(converter) }

    return SpringEncoder(messageConverters)
  }

  @Bean
  fun feignDecoder(): Decoder {
    val objectMapper =
      ObjectMapper().apply {
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      }

    val converter = MappingJackson2HttpMessageConverter(objectMapper)
    val messageConverters = ObjectFactory { HttpMessageConverters(converter) }

    return SpringDecoder(messageConverters)
  }
}
