package ee.tenman.portfolio.configuration

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

private const val MAX_LENGTH = 300

object JsonMapperFactory {
  val instance: JsonMapper =
    JsonMapper
      .builder()
      .addModule(KotlinModule.Builder().build())
      .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
      .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .build()

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
