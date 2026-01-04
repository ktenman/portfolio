package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.model.holding.HoldingKey
import ee.tenman.portfolio.model.holding.HoldingValue
import ee.tenman.portfolio.model.holding.InternalHoldingData
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class HoldingAggregationService {
  fun aggregateHoldings(holdings: List<InternalHoldingData>): Map<HoldingKey, HoldingValue> =
    holdings
      .groupBy { holding -> buildHoldingGroupKey(holding) }
      .entries
      .associate { (_, groupedHoldings) -> buildHoldingEntry(groupedHoldings) }

  private fun buildHoldingGroupKey(holding: InternalHoldingData): String = "name:${normalizeHoldingName(holding.name)}"

  fun normalizeHoldingName(name: String): String =
    removeCompanySuffixes(name.replace(DOMAIN_SUFFIX_REGEX, ""))
      .lowercase()
      .replace(Regex("\\s+"), " ")
      .trim()

  private fun buildHoldingEntry(groupedHoldings: List<InternalHoldingData>): Pair<HoldingKey, HoldingValue> {
    val key = buildHoldingKey(groupedHoldings)
    val value = buildHoldingValue(groupedHoldings)
    return key to value
  }

  private fun buildHoldingKey(groupedHoldings: List<InternalHoldingData>): HoldingKey {
    val first = groupedHoldings.firstOrNull() ?: error("Cannot build key from empty holdings list")
    val bestName = selectBestName(groupedHoldings.map { it.name })
    val longestTicker =
      groupedHoldings
        .mapNotNull { it.ticker?.takeIf { t -> t.isNotBlank() } }
        .maxByOrNull { it.length }
    return HoldingKey(
      holdingUuid = first.holdingUuid,
      ticker = longestTicker,
      name = bestName,
      sector = groupedHoldings.mapNotNull { it.sector }.maxByOrNull { it.length },
      countryCode = groupedHoldings.mapNotNull { it.countryCode }.firstOrNull(),
      countryName = groupedHoldings.mapNotNull { it.countryName }.firstOrNull(),
    )
  }

  private fun selectBestName(names: List<String>): String {
    val namesWithCleanLength =
      names.map { original ->
        val cleaned = removeCompanySuffixes(original.replace(DOMAIN_SUFFIX_REGEX, ""))
        original to cleaned.length
      }
    val bestOriginal = namesWithCleanLength.maxByOrNull { it.second }?.first
    return removeCompanySuffixes(bestOriginal?.replace(DOMAIN_SUFFIX_REGEX, "") ?: names.first())
  }

  private fun buildHoldingValue(groupedHoldings: List<InternalHoldingData>) =
    HoldingValue(
      totalValue = groupedHoldings.fold(BigDecimal.ZERO) { acc, h -> acc.add(h.value) },
      etfSymbols = groupedHoldings.map { it.etfSymbol }.toMutableSet(),
      platforms = groupedHoldings.flatMap { it.platforms }.toMutableSet(),
    )

  companion object {
    private val COMPANY_SUFFIX_REGEX =
      Regex(
        """[,.\-]?\s*(inc\.?|incorporated|corp\.?|corporation|ltd\.?|limited|llc|l\.l\.c\.|""" +
          """plc|p\.l\.c\.|ag|gmbh|kgaa|co\.?|company|sa|s\.a\.|nv|n\.v\.|bv|b\.v\.|""" +
          """srl|s\.r\.l\.|pty|oyj|ab|asa|as|a/s|se|oy|spa|s\.p\.a\.|kg|& co|""" +
          """hldgs?|holdings?|group|grp|enterprises?|technologies|systems?|international|platforms|""" +
          """class [a-z]|cl\.? [a-z]|common stock|ord\.?|ordinary|""" +
          """spon\.?\s*adr|sponsored\s*adr|adr|ads|gdr|depositary|receipt)\.?$""",
        RegexOption.IGNORE_CASE,
      )
    private val DOMAIN_SUFFIX_REGEX = Regex("""\.com|\.net|\.org|\.io""", RegexOption.IGNORE_CASE)

    private fun removeCompanySuffixes(name: String): String {
      var result = name.trim()
      var previous: String
      do {
        previous = result
        result = result.replace(COMPANY_SUFFIX_REGEX, "").trim()
      } while (result != previous)
      return result
    }
  }
}
