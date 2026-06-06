package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.domain.DiversificationConfigData
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.KotlinModule
import java.io.IOException
import java.io.Writer

private const val MAX_LENGTH = 300

object JsonMapperFactory {
  val instance: JsonMapper =
    JsonMapper
      .builder()
      .addModule(KotlinModule.Builder().build())
      .addModule(
        SimpleModule().addDeserializer(
          DiversificationConfigData::class.java,
          DiversificationConfigDataDeserializer(),
        ),
      ).disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
      .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .build()

  fun truncatedJson(value: Any): String {
    val builder = StringBuilder(MAX_LENGTH)
    var truncated = false
    val writer =
      object : Writer() {
        override fun write(
          buffer: CharArray,
          offset: Int,
          length: Int,
        ) {
          if (truncated) return
          val remaining = MAX_LENGTH - builder.length
          if (length <= remaining) {
            builder.append(buffer, offset, length)
            return
          }
          builder.append(buffer, offset, remaining)
          truncated = true
          throw IOException("portfolio log serialization reached truncation limit")
        }

        override fun flush() = Unit

        override fun close() = Unit
      }
    return runCatching {
      instance.writeValue(writer, value)
      builder.toString()
    }.getOrElse { error -> if (truncated) "$builder ..." else throw error }
  }
}
