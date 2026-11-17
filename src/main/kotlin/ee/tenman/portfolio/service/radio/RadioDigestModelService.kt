package ee.tenman.portfolio.service.radio

import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiPurpose
import ee.tenman.portfolio.openrouter.OpenRouterClient
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class RadioDigestModelService(
  private val openRouterClient: OpenRouterClient,
  private val radioProperties: RadioProperties,
) {
  private val judgePrompts: Map<DigestShow, String> = DigestShow.entries.associateWith { resource(it.prompt).trimEnd() }
  private val judgeSchema: Map<String, Any> = JsonMapperFactory.instance.readValue(resource("radio/digest-judge-schema.json"))
  private val clipPrompt: String = resource("radio/digest-clip-prompt.txt").trimEnd()
  private val clipSchema: Map<String, Any> = JsonMapperFactory.instance.readValue(resource("radio/digest-clip-schema.json"))

  fun judge(
    show: DigestShow,
    rows: List<DigestRow>,
    told: List<Pair<LocalDate, String>>,
  ): Judgement? {
    val (earlier, news) = rows.partition { it.earlier }
    val listed = news.joinToString("\n", transform = ::listed)
    val before = if (earlier.isEmpty()) "" else "\n\nEarlier:\n${earlier.joinToString("\n", transform = ::listed)}"
    val history = told.joinToString("\n") { (day, title) -> "${day.format(TOLD)} $title" }
    val user = "$listed$before\n\nTold before:\n$history"
    return ask(judgePrompts.getValue(show), user, judgeSchema)?.let { read(it, Judgement::class.java) }
  }

  fun clip(
    story: JudgedStory,
    lines: List<TranscriptLine>,
  ): ClipAnswer? {
    val start = lines.firstOrNull()?.start ?: 0.0
    val numbered = lines.withIndex().joinToString("\n") { (n, line) -> "${n + 1} | ${position(line.start - start)} | ${line.text}" }
    return ask(clipPrompt, "${story.title}\n${story.summary}\n\n$numbered", clipSchema)?.let { read(it, ClipAnswer::class.java) }
  }

  private fun listed(row: DigestRow): String {
    val minutes = String.format(Locale.ROOT, "%.1f", row.kept / 60)
    val show = "${row.programme} (${row.station.label})"
    return listOf(row.id, row.day.format(DAY), show, "$minutes min", row.title, row.summary.orEmpty()).joinToString(" | ")
  }

  private fun ask(
    system: String,
    user: String,
    schema: Map<String, Any>,
  ): String? = openRouterClient.complete(AiPurpose.RADIO_DIGEST, radioProperties.outlineModel, system, user, schema)

  private fun <T> read(
    text: String,
    type: Class<T>,
  ): T? = runCatching { JsonMapperFactory.instance.readValue(text, type) }.getOrNull()

  companion object {
    private val DAY = DateTimeFormatter.ofPattern("EEE d.M", Locale.ENGLISH)
    private val TOLD = DateTimeFormatter.ofPattern("d.M")
  }
}
