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
    if (!properties.enabled) log.warn("Industry classification disabled, skipping batch of ${companies.size}")
    if (validCompanies.isEmpty() || !properties.enabled) return UNANSWERED
    val response =
      openRouterClient.classifyWithCascadingFallback(buildPrompt(validCompanies), AiModel.primarySectorModel()) ?: run {
        log.warn("Batch industry classification failed for ${validCompanies.size} companies")
        return UNANSWERED
      }
    val results = parse(response.content, validCompanies, response.model)
    val answered = results.size * 2 >= validCompanies.size
    if (!answered) log.warn("Batch industry classification parsed ${results.size} of ${validCompanies.size} lines and counts as unanswered")
    return BatchClassificationOutcome(results, answered)
  }

  private fun buildPrompt(companies: List<CompanyClassificationInput>): String {
    val companiesList =
      companies
        .mapIndexed { index, c ->
          val tickerInfo =
            c.ticker
              ?.takeIf { it.isNotBlank() }
              ?.let { " (${clean(it)})" }
              .orEmpty()
          "${index + 1}. ${clean(c.name)}$tickerInfo"
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

  private fun clean(text: String): String = text.replace(CONTROL_CHARS, " ").take(MAX_NAME_LENGTH)

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
    val LINE_PATTERN = Regex("^(\\d+)[.):]?\\s*(\\d{6})\\b")
    val CONTROL_CHARS = Regex("[\\r\\n\\t]")
    const val MAX_NAME_LENGTH = 120
    val UNANSWERED = BatchClassificationOutcome<GicsIndustryClassificationResult>(emptyMap(), false)
  }
}
