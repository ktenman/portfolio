package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.Currency
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "logo-replacement")
data class LogoReplacementProperties(
  val maxSearchResults: Int = 50,
  val maxDisplayCandidates: Int = 15,
  val parallelValidationThreads: Int = 15,
  val parallelPrefetchThreads: Int = 3,
  val prefetchBatchSize: Int = 10,
  val prefetchBatchDelayMs: Long = 2000L,
  val downloadTimeoutMs: Long = 5000L,
)

@ConfigurationProperties(prefix = "batch-logo-validation")
data class BatchLogoValidationProperties(
  val enabled: Boolean = true,
  val model: AiModel = AiModel.GEMINI_3_5_FLASH_LITE,
  val batchSize: Int = 25,
  val imagesPerCompany: Int = 10,
  val maxTokens: Int = 2000,
  val temperature: Double = 0.0,
  val downloadTimeoutMs: Long = 5000,
  val apiTimeoutMs: Long = 60000,
)

@ConfigurationProperties(prefix = "industry-classification")
data class IndustryClassificationProperties(
  val enabled: Boolean = true,
  val gicsEnabled: Boolean = true,
  val rateLimitBufferMs: Long = 100L,
)

@ConfigurationProperties(prefix = "etf.holding-reconciliation")
data class HoldingReconciliationProperties(
  val enabled: Boolean = false,
  val dryRun: Boolean = true,
)

@ConfigurationProperties(prefix = "fund-currency")
data class FundCurrencyOverridesProperties(
  val overrides: Map<String, String> = emptyMap(),
) {
  fun forSymbol(symbol: String): Currency? = overrides[symbol]?.let(Currency::fromCodeOrNull)

  @PostConstruct
  fun validate() {
    val invalid = overrides.filterValues { Currency.fromCodeOrNull(it) == null }
    check(invalid.isEmpty()) {
      "fund-currency.overrides contains unknown codes: $invalid (allowed: ${Currency.entries.joinToString(", ")})"
    }
  }
}

data class Trading212SymbolEntry(
  var symbol: String = "",
  var ticker: String = "",
)
