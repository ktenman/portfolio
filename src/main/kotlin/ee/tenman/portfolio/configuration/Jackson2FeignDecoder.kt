package ee.tenman.portfolio.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import feign.Response
import feign.codec.DecodeException
import feign.codec.Decoder
import java.io.IOException
import java.lang.reflect.Type

class Jackson2FeignDecoder(
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
    try {
      response.body().asInputStream().use { inputStream ->
        val javaType = objectMapper.typeFactory.constructType(type)
        return objectMapper.readValue(inputStream, javaType)
      }
    } catch (e: IOException) {
      throw DecodeException(response.status(), "Failed to decode response", response.request(), e)
    }
  }
}
