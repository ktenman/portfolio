package ee.tenman.portfolio.configuration

import feign.Response
import feign.codec.DecodeException
import feign.codec.Decoder
import feign.codec.EncodeException
import feign.codec.Encoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

@Configuration
class ObjectMapperConfig {
  @Bean
  fun objectMapper(): ObjectMapper = JsonMapperFactory.instance

  @Bean
  fun feignDecoder(): Decoder {
    val mapper = JsonMapperFactory.instance
    return Decoder { response: Response, type: Type ->
      if (response.body() == null) return@Decoder null
      runCatching<Any?> {
        response.body().asInputStream().use { stream -> mapper.readValue<Any>(stream, mapper.constructType(type)) }
      }.getOrElse { throw DecodeException(response.status(), "Failed to decode response", response.request(), it) }
    }
  }

  @Bean
  fun feignEncoder(): Encoder {
    val mapper = JsonMapperFactory.instance
    return Encoder { obj: Any?, _: Type, template ->
      if (obj == null) return@Encoder
      runCatching {
        template.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        template.body(mapper.writeValueAsBytes(obj), StandardCharsets.UTF_8)
      }.getOrElse { throw EncodeException("Failed to encode request", it) }
    }
  }
}
