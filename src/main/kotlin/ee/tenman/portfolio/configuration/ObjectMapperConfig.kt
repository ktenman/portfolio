package ee.tenman.portfolio.configuration

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

const val MAX_LENGTH = 300

@Configuration
class ObjectMapperConfig {

  companion object {
    val OBJECT_MAPPER: ObjectMapper = ObjectMapper()
      .registerModule(JavaTimeModule())
      .registerKotlinModule()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun truncateJson(json: String): String {
      if (json.length <= MAX_LENGTH) {
        return json
      }

      val halfLength = MAX_LENGTH / 2
      val start = json.substring(0, halfLength)
      val end = json.substring(json.length - halfLength)

      return "$start ... $end"
    }
  }

  @Bean
  fun objectMapper(): ObjectMapper {
    return OBJECT_MAPPER
  }

}
