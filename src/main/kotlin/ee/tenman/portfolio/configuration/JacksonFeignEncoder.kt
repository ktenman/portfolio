package ee.tenman.portfolio.configuration

import feign.RequestTemplate
import feign.codec.EncodeException
import feign.codec.Encoder
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

class JacksonFeignEncoder(
  private val objectMapper: ObjectMapper,
) : Encoder {
  override fun encode(
    obj: Any?,
    bodyType: Type,
    template: RequestTemplate,
  ) {
    if (obj == null) {
      return
    }
    runCatching {
      val json = objectMapper.writeValueAsString(obj)
      template.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      template.body(json.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
    }.getOrElse { e ->
      throw EncodeException("Failed to encode request", e)
    }
  }
}
