package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.openrouter.OpenRouterClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HoldingDeduplicationService(
  private val openRouterClient: OpenRouterClient,
  private val properties: IndustryClassificationProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun confirmDuplicates(candidates: List<HoldingMatchCandidate>): List<HoldingMatchCandidate> {
    if (candidates.isEmpty()) return emptyList()
    val tickerMatches = candidates.filter { it.source == MatchSource.TICKER }
    val nameMatches = candidates.filter { it.source == MatchSource.NAME_SIMILARITY }
    val llmConfirmed = confirmWithLlmIfEnabled(nameMatches)
    return tickerMatches + llmConfirmed
  }

  fun resolveCanonicalLinks(
    confirmedPairs: List<HoldingMatchCandidate>,
    holdings: List<EtfHolding>,
  ): Map<Long, Long> {
    if (confirmedPairs.isEmpty()) return emptyMap()
    val holdingsById = holdings.associateBy { it.id }
    val clusters = computeClusters(confirmedPairs)
    return clusters.flatMap { cluster -> pickCanonicalLinks(cluster, holdingsById) }.toMap()
  }

  private fun confirmWithLlmIfEnabled(candidates: List<HoldingMatchCandidate>): List<HoldingMatchCandidate> {
    if (candidates.isEmpty()) return emptyList()
    if (!properties.enabled) {
      log.warn("Holding dedup disabled, skipping ${candidates.size} candidate pairs")
      return emptyList()
    }
    val prompt = buildBatchPrompt(candidates)
    val response = openRouterClient.classifyWithCascadingFallback(prompt, AiModel.primarySectorModel())
    if (response == null) {
      log.warn("Holding dedup batch failed for ${candidates.size} pairs")
      return emptyList()
    }
    return parseBatchResponse(response.content, candidates)
  }

  private fun buildBatchPrompt(candidates: List<HoldingMatchCandidate>): String {
    val pairsList =
      candidates
        .mapIndexed { index, c -> "${index + 1}. \"${c.firstName}\" vs \"${c.secondName}\"" }
        .joinToString("\n")
    return """
      You are merging duplicate ETF holding company names. For each numbered pair, decide if BOTH names refer to the EXACT SAME company entity.

      Strict rules:
      - Different share classes are NOT the same (e.g. "Alphabet Class A" vs "Alphabet Class C" => NO)
      - Truncated/abbreviated names of the same company ARE the same (e.g. "Taiwan Semiconductor Manufac" vs "Taiwan Semiconductor Manufacturing" => YES)
      - Parent vs subsidiary are NOT the same (e.g. "Alphabet Inc" vs "Google LLC" => NO)
      - Same company with branding suffixes ARE the same (e.g. "Apple" vs "Apple Inc" => YES)

      Reply with one line per pair: number, period, then YES or NO. No explanations.

      Pairs:
      $pairsList

      Reply format:
      1. YES
      2. NO
      """.trimIndent()
  }

  private fun parseBatchResponse(
    content: String?,
    candidates: List<HoldingMatchCandidate>,
  ): List<HoldingMatchCandidate> {
    if (content.isNullOrBlank()) return emptyList()
    val pattern = Regex("(\\d+)\\.?\\s*(YES|NO)", RegexOption.IGNORE_CASE)
    return content
      .lines()
      .mapNotNull { line -> pattern.find(line.trim()) }
      .mapNotNull { match -> toConfirmedCandidate(match, candidates) }
  }

  private fun toConfirmedCandidate(
    match: MatchResult,
    candidates: List<HoldingMatchCandidate>,
  ): HoldingMatchCandidate? {
    val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return null
    if (index !in candidates.indices) return null
    if (!match.groupValues[2].equals("YES", ignoreCase = true)) return null
    return candidates[index]
  }

  private fun computeClusters(pairs: List<HoldingMatchCandidate>): List<Set<Long>> {
    val parent = HashMap<Long, Long>()
    pairs.forEach { pair ->
      parent.putIfAbsent(pair.firstHoldingId, pair.firstHoldingId)
      parent.putIfAbsent(pair.secondHoldingId, pair.secondHoldingId)
      val rootA = findRoot(parent, pair.firstHoldingId)
      val rootB = findRoot(parent, pair.secondHoldingId)
      if (rootA != rootB) parent[rootA] = rootB
    }
    return parent.keys.groupBy { findRoot(parent, it) }.values.map { it.toSet() }
  }

  private fun findRoot(
    parent: HashMap<Long, Long>,
    id: Long,
  ): Long {
    var current = id
    while (true) {
      val next = parent[current] ?: return current
      if (next == current) return current
      val grandparent = parent[next] ?: next
      parent[current] = grandparent
      current = grandparent
    }
  }

  private fun pickCanonicalLinks(
    cluster: Set<Long>,
    holdingsById: Map<Long, EtfHolding>,
  ): List<Pair<Long, Long>> {
    if (cluster.size < 2) return emptyList()
    val items = cluster.mapNotNull { holdingsById[it] }
    if (items.size < 2) return emptyList()
    val canonical =
      items.maxWithOrNull(compareBy({ it.name.length }, { -it.id }))
        ?: return emptyList()
    val canonicalId = canonical.id
    return items.filter { it.id != canonicalId }.map { it.id to canonicalId }
  }
}
