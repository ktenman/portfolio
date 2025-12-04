package ee.tenman.portfolio.configuration

import feign.Response
import feign.codec.DecodeException
import feign.codec.Decoder
import tools.jackson.databind.ObjectMapper
import java.lang.reflect.Type

class JacksonFeignDecoder(
  private val objectMapper: ObjectMapper,
) : Decoder {
  override fun decode(
    response: Response,
    type: Type,
  ): Any? {
    if (response.status() == 404 || response.status() == 204) {
      return null
    }
    if (response.body() == null) {
      return null
    }
    runCatching {
      response.body().asInputStream().use { inputStream ->
        val javaType = objectMapper.constructType(type)
        return objectMapper.readValue(inputStream, javaType)
      }
    }.getOrElse { e ->
      throw DecodeException(response.status(), "Failed to decode response", response.request(), e)
    }
  }
}
