package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.HOLDING_IDENTITY_CACHE
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.dto.IdentityPair
import ee.tenman.portfolio.openrouter.OpenRouterClient
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service

@Service
class HoldingIdentityBatchService(
  private val openRouterClient: OpenRouterClient,
  private val cacheService: HoldingIdentityCacheService,
  private val cacheManager: CacheManager,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun cached(pair: IdentityPair): Boolean? = cache().get(pair.cacheKey)?.get() as Boolean?

  fun resolve(pairs: List<IdentityPair>): Map<IdentityPair, Boolean?> {
    if (pairs.size == 1) return pairs.associateWith { single(it) }
    val content = openRouterClient.classifyWithCascadingFallback(buildPrompt(pairs), AiModel.primarySectorModel())?.content
    val answers = content?.let { parseAnswers(it, pairs.size) }.orEmpty()
    answers.forEach { (index, verdict) -> cache().put(pairs[index].cacheKey, verdict) }
    log.info("Holding identity batch of ${pairs.size} pairs answered ${answers.size}, falling back for ${pairs.size - answers.size}")
    return pairs.withIndex().associate { (index, pair) -> pair to (answers[index] ?: single(pair)) }
  }

  private fun single(pair: IdentityPair): Boolean? = cacheService.resolve(pair)

  private fun parseAnswers(
    content: String,
    size: Int,
  ): Map<Int, Boolean> =
    content
      .lineSequence()
      .mapNotNull { ANSWER_LINE.find(it) }
      .map { it.groupValues[1].toInt() - 1 to it.groupValues[2].equals("YES", ignoreCase = true) }
      .filter { (index, _) -> index in 0 until size }
      .groupBy({ it.first }, { it.second })
      .filterValues { it.size == 1 }
      .mapValues { it.value.single() }

  private fun buildPrompt(pairs: List<IdentityPair>): String {
    val lines = pairs.withIndex().joinToString("\n") { (index, pair) -> "${index + 1}. ${describe(pair)}" }
    return """
      |You are deduplicating ETF holding names coming from different data providers.
      |Decide for each numbered pair below whether both names denote the same legal entity.
      |
      |$lines
      |
      |${HoldingIdentityCacheService.IDENTITY_RULES}
      |
      |Answer with exactly one line per pair, in the form "<number>: YES" or "<number>: NO", and nothing else.
      """.trimMargin()
  }

  private fun describe(pair: IdentityPair): String {
    val ticker = pair.ticker?.takeIf { it.isNotBlank() }?.let { " | Possible shared ticker: ${LogSanitizerUtil.sanitize(it)}" } ?: ""
    return "Name 1: ${LogSanitizerUtil.sanitize(pair.existingName)} | Name 2: ${LogSanitizerUtil.sanitize(pair.candidateName)}$ticker"
  }

  private fun cache(): Cache =
    requireNotNull(cacheManager.getCache(HOLDING_IDENTITY_CACHE)) { "Cache $HOLDING_IDENTITY_CACHE is not configured" }

  companion object {
    private val ANSWER_LINE = Regex("""^\s*(\d{1,4})\s*[:.)-]\s*(YES|NO)\b""", RegexOption.IGNORE_CASE)
  }
}
