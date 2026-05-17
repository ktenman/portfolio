package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfHolding
import org.springframework.stereotype.Service

@Service
class HoldingSimilarityPrefilterService {
  fun findCandidatePairs(holdings: List<EtfHolding>): List<HoldingMatchCandidate> {
    if (holdings.size < 2) return emptyList()
    val tickerMatches = findTickerMatches(holdings)
    val nameMatches = findNameSimilarityMatches(holdings)
    return tickerMatches + nameMatches
  }

  fun isSimilarName(
    nameA: String,
    nameB: String,
  ): Boolean {
    val a = normalize(nameA)
    val b = normalize(nameB)
    return hasSubstringOverlap(a, b) || tokenJaccard(a, b) >= MIN_JACCARD
  }

  private fun findTickerMatches(holdings: List<EtfHolding>): List<HoldingMatchCandidate> =
    holdings
      .filter { !it.ticker.isNullOrBlank() }
      .groupBy { it.ticker!!.trim().uppercase() }
      .filter { it.value.size > 1 }
      .flatMap { (_, group) -> buildPairsFromGroup(group, MatchSource.TICKER) }

  private fun findNameSimilarityMatches(holdings: List<EtfHolding>): List<HoldingMatchCandidate> {
    val sorted = holdings.sortedBy { it.id }
    return sorted.flatMapIndexed { index, first ->
      sorted
        .drop(index + 1)
        .filter { second -> !sameTicker(first, second) && isSimilarName(first.name, second.name) }
        .map { second -> toCandidate(first, second, MatchSource.NAME_SIMILARITY) }
    }
  }

  private fun buildPairsFromGroup(
    group: List<EtfHolding>,
    source: MatchSource,
  ): List<HoldingMatchCandidate> {
    val sorted = group.sortedBy { it.id }
    return sorted.flatMapIndexed { index, first ->
      sorted.drop(index + 1).map { second -> toCandidate(first, second, source) }
    }
  }

  private fun sameTicker(
    a: EtfHolding,
    b: EtfHolding,
  ): Boolean {
    val tickerA = a.ticker?.trim().orEmpty()
    val tickerB = b.ticker?.trim().orEmpty()
    if (tickerA.isBlank() || tickerB.isBlank()) return false
    return tickerA.equals(tickerB, ignoreCase = true)
  }

  private fun hasSubstringOverlap(
    a: String,
    b: String,
  ): Boolean {
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    if (shorter.length < MIN_OVERLAP_CHARS) return false
    return longer.contains(shorter)
  }

  private fun tokenJaccard(
    a: String,
    b: String,
  ): Double {
    val tokensA = a.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
    val tokensB = b.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
    if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
    val intersection = tokensA.intersect(tokensB).size
    val union = tokensA.union(tokensB).size
    return intersection.toDouble() / union.toDouble()
  }

  private fun normalize(name: String): String =
    name
      .lowercase()
      .replace(Regex("\\s+"), " ")
      .trim()

  private fun toCandidate(
    a: EtfHolding,
    b: EtfHolding,
    source: MatchSource,
  ): HoldingMatchCandidate {
    val (first, second) = if (a.id <= b.id) a to b else b to a
    return HoldingMatchCandidate(
      firstHoldingId = first.id,
      firstName = first.name,
      firstTicker = first.ticker,
      secondHoldingId = second.id,
      secondName = second.name,
      secondTicker = second.ticker,
      source = source,
    )
  }

  companion object {
    private const val MIN_OVERLAP_CHARS = 10
    private const val MIN_JACCARD = 0.7
  }
}
