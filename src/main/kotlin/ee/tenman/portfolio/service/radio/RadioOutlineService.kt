package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.openrouter.OpenRouterClient
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.readValue

@Service
class RadioOutlineService(
  private val openRouterClient: OpenRouterClient,
) {
  private val prompt: String = resource("radio/outline-prompt.txt").trimEnd()
  private val schema: Map<String, Any> = JsonMapperFactory.instance.readValue(resource("radio/outline-schema.json"))

  fun reads(
    show: Show,
    text: String,
    model: AiModel,
    read: () -> Unit,
  ): List<OutlineRead?> {
    val asked = prompt.replace("{programme}", show.name).replace("{minutes}", "${show.minutes}")
    return List(OUTLINE_READS) { openRouterClient.complete(model, asked, text, schema)?.let(::parse).also { read() } }
  }

  private fun resource(path: String): String = checkNotNull(javaClass.classLoader.getResource(path)) { "Missing resource $path" }.readText()
}
