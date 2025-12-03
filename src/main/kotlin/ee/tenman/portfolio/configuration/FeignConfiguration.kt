package ee.tenman.portfolio.configuration

import feign.codec.Decoder
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder
import org.springframework.cloud.openfeign.support.SpringDecoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import tools.jackson.databind.json.JsonMapper

@Configuration
class FeignConfiguration {
  @Bean
  fun jacksonJsonHttpMessageConverter(jsonMapper: JsonMapper): JacksonJsonHttpMessageConverter = JacksonJsonHttpMessageConverter(jsonMapper)

  @Bean
  fun httpMessageConverters(): HttpMessageConverters =
    HttpMessageConverters
      .forClient()
      .registerDefaults()
      .build()

  @Bean
  fun feignHttpMessageConverters(
    converters: ObjectProvider<HttpMessageConverter<*>>,
    customizers: ObjectProvider<HttpMessageConverterCustomizer>,
  ): FeignHttpMessageConverters = FeignHttpMessageConverters(converters, customizers)

  @Bean
  fun feignDecoder(feignHttpMessageConvertersProvider: ObjectProvider<FeignHttpMessageConverters>): Decoder {
    feignHttpMessageConvertersProvider.getObject().converters
    return ResponseEntityDecoder(SpringDecoder(feignHttpMessageConvertersProvider))
  }
}
