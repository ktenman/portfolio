package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.HOLDING_IDENTITY_CACHE
import ee.tenman.portfolio.domain.AiModel
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
    key = "#existingName + '|' + #candidateName + '|' + (#ticker ?: '')",
    unless = "#result == false",
  )
  fun isSameCompany(
    existingName: String,
    candidateName: String,
    ticker: String?,
  ): Boolean {
    if (!properties.enabled) return false
    if (existingName.isBlank() || candidateName.isBlank()) return false
    if (existingName.equals(candidateName, ignoreCase = true)) return true
    val prompt = buildPrompt(existingName, candidateName, ticker)
    val response = openRouterClient.classifyWithCascadingFallback(prompt, AiModel.primarySectorModel()) ?: return false
    val verdict = response.content?.trim()?.startsWith("YES", ignoreCase = true) ?: false
    log.info(
      "Holding identity check '{}' vs '{}' resolved to {}",
      LogSanitizerUtil.sanitize(existingName),
      LogSanitizerUtil.sanitize(candidateName),
      verdict,
    )
    return verdict
  }

  private fun buildPrompt(
    existingName: String,
    candidateName: String,
    ticker: String?,
  ): String {
    val tickerLine = ticker?.takeIf { it.isNotBlank() }?.let { "They may share the ticker symbol $it.\n" } ?: ""
    return """
      Two stock holding names need to be compared.
      ${tickerLine}Name 1: $existingName
      Name 2: $candidateName

      Are these the SAME company? Answer YES only if they are the same legal entity (abbreviation, legal-form suffix, rebrand, or alternate spelling). Answer NO if they are different companies that merely look similar or share a ticker.

      ANSWER WITH ONLY ONE WORD: YES or NO.
      """.trimIndent()
  }
}
