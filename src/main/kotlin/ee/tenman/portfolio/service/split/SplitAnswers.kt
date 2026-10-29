package ee.tenman.portfolio.service.split

import ee.tenman.portfolio.configuration.JsonMapperFactory
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

data class SplitAnswer(
  val symbol: String,
  val announced: Boolean? = null,
  val ratio: String? = null,
  val effectiveDate: LocalDate? = null,
  val sourceUrl: String? = null,
  val publishedDate: LocalDate? = null,
)

data class AnnouncedSplit(
  val symbol: String,
  val ratio: String,
  val effectiveDate: LocalDate,
  val sourceUrl: String,
  val publishedDate: LocalDate?,
)

private val RATIO = Regex("[1-9]\\d*:[1-9]\\d*")

private val WEB_ADDRESS = Regex("https?://\\S+")

fun parseSplits(
  text: String,
  symbols: Set<String>,
  today: LocalDate,
): List<AnnouncedSplit> {
  val answers = read(text)
  val answered = answers.map { it.symbol }.sorted()
  check(answered == symbols.sorted()) { "Split answer covers $answered instead of ${symbols.sorted()}" }
  return answers
    .filter { checkNotNull(it.announced) { "Split answer for ${it.symbol} does not say whether a split is announced" } }
    .map(::announced)
    .filterNot { it.effectiveDate.isBefore(today) }
}

private fun read(text: String): List<SplitAnswer> {
  val start = text.indexOf('[')
  val end = text.lastIndexOf(']')
  check(start >= 0 && end > start) { "Split answer holds no JSON array: ${text.take(200)}" }
  return runCatching { JsonMapperFactory.instance.readValue<List<SplitAnswer>>(text.substring(start, end + 1)) }
    .getOrElse { error("Split answer is not a JSON array of answers: ${it.message}") }
}

private fun announced(answer: SplitAnswer): AnnouncedSplit =
  AnnouncedSplit(
    symbol = answer.symbol,
    ratio = checkNotNull(answer.ratio?.takeIf(RATIO::matches)) { "Split answer for ${answer.symbol} has ratio ${answer.ratio}" },
    effectiveDate = checkNotNull(answer.effectiveDate) { "Split answer for ${answer.symbol} has no effective date" },
    sourceUrl =
      checkNotNull(answer.sourceUrl?.takeIf(WEB_ADDRESS::matches)) { "Split answer for ${answer.symbol} has source ${answer.sourceUrl}" },
    publishedDate = answer.publishedDate,
  )
