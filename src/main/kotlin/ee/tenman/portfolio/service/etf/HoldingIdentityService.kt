package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.HOLDING_IDENTITY_CACHE
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.HoldingNameSimilarity
import ee.tenman.portfolio.openrouter.OpenRouterClient
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class HoldingIdentityService(
  private val openRouterClient: OpenRouterClient,
  private val properties: IndustryClassificationProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Cacheable(
    value = [HOLDING_IDENTITY_CACHE],
    key = "#existingName.length() + '|' + #existingName + '|' + #candidateName.length() + '|' + #candidateName + '|' + (#ticker ?: '')",
    unless = "#result == null",
  )
  fun isSameCompany(
    existingName: String,
    candidateName: String,
    ticker: String?,
  ): Boolean? {
    if (!properties.enabled) return null
    if (existingName.isBlank() || candidateName.isBlank()) return null
    if (existingName.equals(candidateName, ignoreCase = true)) return true
    if (!HoldingNameSimilarity.mayBeSameCompany(existingName, candidateName)) return false
    val prompt = buildPrompt(existingName, candidateName, ticker)
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

  private fun buildPrompt(
    existingName: String,
    candidateName: String,
    ticker: String?,
  ): String {
    val tickerLine = ticker?.takeIf { it.isNotBlank() }?.let { "They may share the ticker symbol $it.\n" } ?: ""
    return """
      |You are deduplicating ETF holding names coming from different data providers.
      |
      |${tickerLine}Name 1: $existingName
      |Name 2: $candidateName
      |
      |Answer YES when both names denote the same legal entity. Providers mangle names, so YES still applies
      |when the only differences are:
      |- a truncated or abbreviated name ("Zhejiang Sanhua Intelligen-h", "Kingdee Intl Sft", "Bharat Heavy Ele")
      |- legal-form or listing suffixes (Ltd, Sa, Pcl, Pjsc, -a, Class B, ADR, GDR, Non-voting, Pref, Jpy50)
      |- a ticker abbreviation or a rebrand of the same entity (GSK / GlaxoSmithKline, Strategy / MicroStrategy)
      |- translation, transliteration or a spelling variant (Sberbank Rossii / Sberbank of Russia, Munich Re / Muenchener Rueck)
      |
      |Answer NO when the names denote different legal entities, even if they are closely related:
      |- separate listed subsidiaries or affiliates of one group (Adani Ports vs Adani Enterprises, Alibaba vs Ant Group)
      |- companies sharing a place name, family name, or industry word (China Merchants Bank vs China Life Insurance)
      |- a parent and its separately listed subsidiary
      |
      |ANSWER WITH ONLY ONE WORD: YES or NO.
      """.trimMargin()
  }
}
