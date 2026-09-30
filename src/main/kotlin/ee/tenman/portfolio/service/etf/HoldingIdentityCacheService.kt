package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.HOLDING_IDENTITY_CACHE
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.dto.IdentityPair
import ee.tenman.portfolio.openrouter.OpenRouterClient
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class HoldingIdentityCacheService(
  private val openRouterClient: OpenRouterClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Cacheable(
    value = [HOLDING_IDENTITY_CACHE],
    key = "#pair.cacheKey",
    unless = "#result == null",
  )
  fun resolve(pair: IdentityPair): Boolean? {
    val (existingName, candidateName) = pair
    val prompt = buildPrompt(pair)
    val content = openRouterClient.classifyWithCascadingFallback(prompt, AiModel.primarySectorModel())?.content ?: return null
    val verdict = parseVerdict(content)
    if (verdict == null) {
      log.warn(
        "Holding identity answer for '${LogSanitizerUtil.sanitize(existingName)}' " +
          "vs '${LogSanitizerUtil.sanitize(candidateName)}' was not a clear YES or NO",
      )
      return null
    }
    log.info(
      "Holding identity check '${LogSanitizerUtil.sanitize(existingName)}' " +
        "vs '${LogSanitizerUtil.sanitize(candidateName)}' resolved to $verdict",
    )
    return verdict
  }

  private fun parseVerdict(content: String): Boolean? {
    val normalized = content.trim()
    return when {
      normalized.startsWith("YES", ignoreCase = true) -> true
      normalized.startsWith("NO", ignoreCase = true) -> false
      else -> null
    }
  }

  private fun buildPrompt(pair: IdentityPair): String {
    val tickerLine = pair.ticker?.takeIf { it.isNotBlank() }?.let { "They may share the ticker symbol $it.\n" } ?: ""
    return """
      |You are deduplicating ETF holding names coming from different data providers.
      |
      |${tickerLine}Name 1: ${pair.existingName}${countrySuffix(pair.existingCountry, pair)}
      |Name 2: ${pair.candidateName}${countrySuffix(pair.candidateCountry, pair)}
      |
      |$IDENTITY_RULES
      |
      |ANSWER WITH ONLY ONE WORD: YES or NO.
      """.trimMargin()
  }

  companion object {
    fun countrySuffix(
      country: String?,
      pair: IdentityPair,
    ): String = if (pair.countryConflict) " (country: $country)" else ""

    val IDENTITY_RULES =
      """
      |Answer YES when both names denote the same legal entity. Providers mangle names, so YES still applies
      |when the only differences are:
      |- a truncated or abbreviated name ("Zhejiang Sanhua Intelligen-h", "Kingdee Intl Sft", "Bharat Heavy Ele")
      |- legal-form or listing suffixes (Ltd, Sa, Pcl, Pjsc, -a, Class B, ADR, GDR, Non-voting, Pref, Jpy50)
      |- a ticker abbreviation or a rebrand of the same entity (GSK / GlaxoSmithKline, Strategy / MicroStrategy)
      |- translation, transliteration or a spelling variant (Sberbank Rossii / Sberbank of Russia, Munich Re / Muenchener Rueck)
      |- a dual listing of one company in two countries (Rio Tinto in the United Kingdom and Australia)
      |
      |Answer NO when the names denote different legal entities, even if they are closely related:
      |- separate listed subsidiaries or affiliates of one group (Adani Ports vs Adani Enterprises, Alibaba vs Ant Group)
      |- companies sharing a place name, family name, or industry word (China Merchants Bank vs China Life Insurance)
      |- a parent and its separately listed subsidiary
      |- unrelated companies in different countries sharing a name or ticker (Merck & Co in the US vs Merck KGaA in Germany)
      """.trimMargin()
  }
}
