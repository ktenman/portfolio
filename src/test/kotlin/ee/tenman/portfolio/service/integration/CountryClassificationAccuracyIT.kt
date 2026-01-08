package ee.tenman.portfolio.service.integration

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.openrouter.OpenRouterClient
import jakarta.annotation.Resource
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

@IntegrationTest
class CountryClassificationAccuracyIT {
  private val log = LoggerFactory.getLogger(javaClass)

  @Resource
  private lateinit var openRouterClient: OpenRouterClient

  @Resource
  private lateinit var countryClassificationService: CountryClassificationService

  companion object {
    val GROUND_TRUTH: Map<String, String> =
      mapOf(
        "Apple Inc." to "US",
        "Microsoft Corporation" to "US",
        "Amazon.com Inc." to "US",
        "Alphabet Inc." to "US",
        "Meta Platforms Inc." to "US",
        "Tesla Inc." to "US",
        "NVIDIA Corporation" to "US",
        "Intel Corporation" to "US",
        "AMD" to "US",
        "Qualcomm Inc." to "US",
        "Broadcom Inc." to "US",
        "Adobe Inc." to "US",
        "Salesforce Inc." to "US",
        "Oracle Corporation" to "US",
        "IBM" to "US",
        "Coca-Cola Company" to "US",
        "PepsiCo Inc." to "US",
        "McDonald's Corporation" to "US",
        "Walt Disney Company" to "US",
        "Netflix Inc." to "US",
        "Samsung Electronics Co Ltd" to "KR",
        "Hyundai Motor Company" to "KR",
        "SK Hynix Inc." to "KR",
        "Toyota Motor Corporation" to "JP",
        "Sony Group Corporation" to "JP",
        "Honda Motor Co Ltd" to "JP",
        "Nintendo Co Ltd" to "JP",
        "Volkswagen AG" to "DE",
        "BMW AG" to "DE",
        "Mercedes-Benz Group AG" to "DE",
        "Siemens AG" to "DE",
        "SAP SE" to "DE",
        "Deutsche Bank AG" to "DE",
        "LVMH Moët Hennessy Louis Vuitton" to "FR",
        "TotalEnergies SE" to "FR",
        "L'Oréal SA" to "FR",
        "Airbus SE" to "FR",
        "Nestlé SA" to "CH",
        "Roche Holding AG" to "CH",
        "Novartis AG" to "CH",
        "UBS Group AG" to "CH",
        "HSBC Holdings plc" to "GB",
        "BP plc" to "GB",
        "AstraZeneca plc" to "GB",
        "Unilever plc" to "GB",
        "Shell plc" to "GB",
        "Barclays plc" to "GB",
        "Ferrari N.V." to "IT",
        "Alibaba Group Holding Ltd" to "CN",
        "Tencent Holdings Ltd" to "CN",
        "BYD Company Ltd" to "CN",
      )

    val MODELS_TO_TEST =
      listOf(
        AiModel.CLAUDE_OPUS_4_5,
        AiModel.CLAUDE_SONNET_4_5,
        AiModel.DEEPSEEK_V3_2,
      )
  }

  @Test
  @Disabled("Manual test for model accuracy comparison - run locally")
  fun `compare country classification accuracy across models`() {
    val results = mutableMapOf<AiModel, ModelTestResult>()
    MODELS_TO_TEST.forEach { model ->
      log.info("Testing model: ${model.modelId}")
      val modelResult = testModel(model)
      results[model] = modelResult
      log.info("Model ${model.modelId}: ${modelResult.correctCount}/${modelResult.totalCount} correct (${modelResult.accuracyPercent}%)")
      Thread.sleep(2000)
    }
    log.info("\n=== ACCURACY COMPARISON ===")
    results.entries
      .sortedByDescending { it.value.accuracyPercent }
      .forEach { (model, result) ->
        log.info("${model.modelId}: ${result.accuracyPercent}% (${result.correctCount}/${result.totalCount})")
        if (result.errors.isNotEmpty()) {
          log.info("  Errors:")
          result.errors.forEach { error ->
            log.info("    - ${error.company}: expected ${error.expected}, got ${error.actual}")
          }
        }
      }
  }

  @Test
  @Disabled("Manual test - run locally to verify specific companies")
  fun `test specific problematic companies`() {
    val problematicCompanies =
      listOf(
        "Ferrari N.V." to "IT",
        "Airbus SE" to "FR",
        "Shell plc" to "GB",
        "Unilever plc" to "GB",
        "Linde plc" to "IE",
        "ASML Holding N.V." to "NL",
      )
    MODELS_TO_TEST.forEach { model ->
      log.info("\n=== Testing ${model.modelId} ===")
      problematicCompanies.forEach { (company, expected) ->
        val result = classifyWithModel(company, model)
        val status = if (result == expected) "✓" else "✗"
        log.info("$status $company: expected=$expected, got=$result")
        Thread.sleep(1500)
      }
    }
  }

  private fun testModel(model: AiModel): ModelTestResult {
    var correct = 0
    val errors = mutableListOf<ClassificationError>()
    val testSubset = GROUND_TRUTH.entries.take(20)
    testSubset.forEach { (company, expectedCountry) ->
      val result = classifyWithModel(company, model)
      if (result == expectedCountry) {
        correct++
      } else {
        errors.add(ClassificationError(company, expectedCountry, result ?: "null"))
      }
      Thread.sleep(1000)
    }
    return ModelTestResult(
      correctCount = correct,
      totalCount = testSubset.size,
      errors = errors,
    )
  }

  private fun classifyWithModel(
    company: String,
    model: AiModel,
  ): String? {
    val prompt = buildPrompt(company)
    val response = openRouterClient.classifyWithCascadingFallback(prompt, model) ?: return null
    val content = response.content ?: return null
    return parseCountryCode(content)
  }

  private fun buildPrompt(companyName: String): String =
    """
    What is the headquarters country of "$companyName"?

    IMPORTANT: Answer with the country where the company's OPERATIONAL headquarters is located,
    not just where it is legally incorporated for tax purposes.

    Examples:
    - Ferrari: Answer IT (Italy, Maranello) - not NL even though incorporated there
    - Shell: Answer GB (UK, London) - moved from NL in 2022
    - Unilever: Answer GB (UK, London) - unified structure since 2020
    - ASML: Answer NL (Netherlands, Veldhoven) - actually Dutch company

    ANSWER WITH ONLY THE 2-LETTER ISO COUNTRY CODE. DO NOT EXPLAIN.

    Country code:
    """.trimIndent()

  private fun parseCountryCode(content: String): String? {
    val trimmed =
      content
        .trim()
        .uppercase()
    val isoCountries = java.util.Locale.getISOCountries()
    if (trimmed.length == 2 && isoCountries.contains(trimmed)) {
      return trimmed
    }
    return null
  }

  data class ModelTestResult(
    val correctCount: Int,
    val totalCount: Int,
    val errors: List<ClassificationError>,
  ) {
    val accuracyPercent: Double
      get() = if (totalCount > 0) (correctCount.toDouble() / totalCount * 100) else 0.0
  }

  data class ClassificationError(
    val company: String,
    val expected: String,
    val actual: String,
  )

  @Test
  fun `ground truth dataset should have at least 50 entries`() {
    expect(GROUND_TRUTH.size).toBeGreaterThanOrEqualTo(50)
  }

  @Test
  fun `ground truth should have valid country codes`() {
    val validCodes =
      java.util.Locale
        .getISOCountries()
        .toSet()
    GROUND_TRUTH.values.forEach { code ->
      expect(validCodes.contains(code)).toEqual(true)
    }
  }
}
