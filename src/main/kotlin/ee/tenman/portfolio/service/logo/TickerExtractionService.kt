package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.openrouter.OpenRouterClient
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TickerExtractionService(
  private val openRouterClient: OpenRouterClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun extractTicker(companyName: String): TickerExtractionResult? {
    if (companyName.isBlank()) return null
    val sanitizedName = LogSanitizerUtil.sanitize(companyName)
    log.debug("Extracting ticker for company: $sanitizedName")
    val prompt = buildPrompt(companyName)
    val response =
      openRouterClient.classifyWithCascadingFallback(
      prompt = prompt,
      startingModel = AiModel.CLAUDE_OPUS_4_5,
      maxTokens = 20,
      temperature = 0.0,
    ) ?: return null
    val ticker = parseTickerFromResponse(response.content) ?: return null
    log.info("Extracted ticker '$ticker' for company: $sanitizedName")
    return TickerExtractionResult(ticker = ticker, model = response.model)
  }

  private fun parseTickerFromResponse(content: String?): String? {
    if (content.isNullOrBlank()) return null
    val ticker = content.trim().uppercase()
    if (!TICKER_PATTERN.matches(ticker)) return null
    if (ticker in INVALID_RESPONSES) return null
    return ticker
  }

  private fun buildPrompt(companyName: String): String =
    """
    Extract the stock ticker symbol for: $companyName

    Examples:
    - Apple Inc → AAPL
    - Microsoft Corporation → MSFT
    - NVIDIA Corporation → NVDA
    - Tesla Inc → TSLA
    - Alphabet Inc → GOOGL
    - Amazon.com Inc → AMZN
    - Meta Platforms Inc → META
    - Taiwan Semiconductor → TSM
    - Samsung Electronics → 005930
    - Nestle SA → NESN

    RESPOND WITH ONLY THE TICKER SYMBOL. If unknown, respond with UNKNOWN.

    Ticker:
    """.trimIndent()

  companion object {
    private val TICKER_PATTERN = Regex("^[A-Z0-9]{1,10}$")
    private val INVALID_RESPONSES = setOf("UNKNOWN", "N/A", "NA", "NONE", "NULL")
  }
}
