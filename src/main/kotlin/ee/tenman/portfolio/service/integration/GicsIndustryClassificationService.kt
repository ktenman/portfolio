package ee.tenman.portfolio.service.integration

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.openrouter.OpenRouterClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GicsIndustryClassificationService(
  private val openRouterClient: OpenRouterClient,
  private val properties: IndustryClassificationProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun classifyBatch(companies: List<CompanyClassificationInput>): BatchClassificationOutcome<GicsIndustryClassificationResult> {
    val validCompanies = companies.filter { it.name.isNotBlank() }
    if (validCompanies.isEmpty()) return BatchClassificationOutcome(emptyMap(), false)
    if (!properties.enabled) {
      log.warn("Industry classification disabled, skipping batch of ${companies.size}")
      return BatchClassificationOutcome(emptyMap(), false)
    }
    return classifyValidBatch(validCompanies)
  }

  private fun classifyValidBatch(
    validCompanies: List<CompanyClassificationInput>,
  ): BatchClassificationOutcome<GicsIndustryClassificationResult> {
    val response =
      openRouterClient.classifyWithCascadingFallback(buildPrompt(validCompanies), AiModel.primarySectorModel()) ?: run {
        log.warn("Batch industry classification failed for ${validCompanies.size} companies")
        return BatchClassificationOutcome(emptyMap(), false)
      }
    return BatchClassificationOutcome(parse(response.content, validCompanies, response.model), true)
  }

  private fun buildPrompt(companies: List<CompanyClassificationInput>): String {
    val companiesList =
      companies
        .mapIndexed { index, c ->
          val tickerInfo = if (!c.ticker.isNullOrBlank()) " (${c.ticker})" else ""
          "${index + 1}. ${c.name}$tickerInfo"
        }.joinToString("\n")
    return """
      |Classify each company into ONE GICS industry. Reply with the company number and the six-digit GICS code only.
      |
      |GICS industries (code name):
      |${GicsIndustry.promptCatalogue()}
      |
      |Rules:
      |- Use only codes from the list above
      |- One line per company, format "1. 453010"
      |- No explanations, no extra text
      |
      |Companies:
      |$companiesList
      |
      |Reply format (one per line):
      |1. 453010
      |2. 401010
      |...
      """.trimMargin()
  }

  private fun parse(
    content: String?,
    companies: List<CompanyClassificationInput>,
    model: AiModel?,
  ): Map<Long, GicsIndustryClassificationResult> =
    content
      .orEmpty()
      .lines()
      .mapNotNull { line ->
        val match = LINE_PATTERN.find(line.trim()) ?: return@mapNotNull null
        val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@mapNotNull null
        val company = companies.getOrNull(index) ?: return@mapNotNull null
        val industry = GicsIndustry.fromCode(match.groupValues[2].toInt()) ?: return@mapNotNull null
        company.holdingId to GicsIndustryClassificationResult(industry = industry, model = model)
      }.toMap()

  private companion object {
    val LINE_PATTERN = Regex("^(\\d+)\\.?\\s*(\\d{6})\\b")
  }
}
