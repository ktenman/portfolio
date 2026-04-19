package ee.tenman.portfolio.service.instrument

import ee.tenman.portfolio.configuration.JsonMapperFactory
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.openrouter.OpenRouterClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FundCurrencyLlmLookupService(
  private val openRouterClient: OpenRouterClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val mapper = JsonMapperFactory.instance

  fun lookup(instrument: Instrument): Currency? {
    val prompt = buildPrompt(instrument)
    val raw = openRouterClient.classifyWithOnlineSearch(AiModel.GPT_5_4_NANO, prompt) ?: return null
    return parseAndValidate(raw, instrument.symbol)
  }

  private fun buildPrompt(instrument: Instrument): String =
    """
    You are a financial data assistant. Find the fund base currency (NAV denomination currency) for this ETF:
      Symbol: ${sanitize(instrument.symbol)}
      Name: ${sanitize(instrument.name)}

    Search official issuer sources (KIID, factsheet, prospectus). Reply with JSON only:
      {"currency": "USD"} — where value is exactly one 3-letter ISO code
      {"currency": null} — if you cannot confirm from a citable source

    Allowed codes: $ALLOWED_CODES.
    Do not guess. If unsure, return null.
    """.trimIndent()

  private fun sanitize(s: String): String = s.replace(CONTROL_CHARS, " ").take(120)

  private fun parseAndValidate(
    raw: String,
    symbol: String,
  ): Currency? {
    val parsed = runCatching { mapper.readValue(raw, Map::class.java) }.getOrNull()
    val rawCode = parsed?.get("currency") as? String
    val currency = rawCode?.let { Currency.fromCodeOrNull(it) }
    when {
      parsed == null -> log.warn("LLM returned unparseable content for $symbol: ${raw.take(200)}")
      rawCode == null -> log.info("LLM returned null currency for $symbol")
      currency == null -> log.warn("LLM returned non-allowlist currency '${rawCode.uppercase()}' for $symbol")
      else -> log.info("LLM resolved fund currency $currency for $symbol")
    }
    return currency
  }

  companion object {
    private val CONTROL_CHARS = Regex("[\\r\\n\\t]")
    private val ALLOWED_CODES = Currency.entries.joinToString(", ") { it.name }
  }
}
