package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.HoldingNameSimilarity
import ee.tenman.portfolio.dto.IdentityPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service

@Service
class HoldingIdentityService(
  private val batchService: HoldingIdentityBatchService,
  private val properties: IndustryClassificationProperties,
) {
  fun resolveAll(pairs: Collection<IdentityPair>): Map<IdentityPair, Boolean?> {
    val distinct = pairs.distinct()
    if (!properties.enabled) return distinct.associateWith { null }
    val known =
      distinct
        .filterNot { it.existingName.isBlank() || it.candidateName.isBlank() }
        .associateWith { rule(it) ?: batchService.cached(it) }
    return distinct.associateWith { null } + known + resolveMisses(known.filterValues { it == null }.keys.toList())
  }

  private fun rule(pair: IdentityPair): Boolean? =
    when {
      pair.existingName.equals(pair.candidateName, ignoreCase = true) -> true
      !pair.countryConflict && HoldingNameSimilarity.isSameName(pair.existingName, pair.candidateName) -> true
      !HoldingNameSimilarity.mayBeSameCompany(pair.existingName, pair.candidateName) -> false
      else -> null
    }

  private fun resolveMisses(misses: List<IdentityPair>): Map<IdentityPair, Boolean?> =
    runBlocking(Dispatchers.IO.limitedParallelism(PARALLELISM)) {
      misses.chunked(BATCH_SIZE).map { async { batchService.resolve(it) } }.awaitAll()
    }.flatMap { it.toList() }.toMap()

  companion object {
    private const val BATCH_SIZE = 40
    private const val PARALLELISM = 4
  }
}
