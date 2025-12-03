package ee.tenman.portfolio.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import feign.RequestTemplate
import feign.codec.EncodeException
import feign.codec.Encoder
import java.io.IOException
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

class Jackson2FeignEncoder(
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
    try {
      val json = objectMapper.writeValueAsString(obj)
      template.body(json.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
    } catch (e: IOException) {
      throw EncodeException("Failed to encode request", e)
    }
  }
}
