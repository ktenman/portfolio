package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.model.HoldingMergePlan
import ee.tenman.portfolio.model.ReconciliationResult
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HoldingReconciliationService(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val holdingIdentityService: HoldingIdentityService,
  private val holdingMergeService: HoldingMergeService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun reconcile(dryRun: Boolean): ReconciliationResult {
    val blockKeys = etfHoldingRepository.findDuplicateBlockKeys()
    if (blockKeys.isEmpty()) return ReconciliationResult(mergedGroups = 0, mergedDuplicates = 0)
    log.info("Reconciling ${blockKeys.size} candidate duplicate block keys (dryRun=$dryRun)")
    val plans = blockKeys.flatMap { planMergesForBlock(it) }
    plans.forEach { logPlan(it) }
    if (!dryRun) plans.forEach { holdingMergeService.merge(it.canonicalId, it.duplicateIds) }
    return ReconciliationResult(mergedGroups = plans.size, mergedDuplicates = plans.sumOf { it.duplicateIds.size })
  }

  private fun planMergesForBlock(blockKey: String): List<HoldingMergePlan> {
    val holdings = etfHoldingRepository.findByNameBlockKey(blockKey).sortedBy { it.id }
    if (holdings.size < 2) return emptyList()
    return clusterByIdentity(holdings)
      .filter { it.size >= 2 }
      .map { cluster ->
        HoldingMergePlan(
          canonicalId = cluster.first().id,
          canonicalName = cluster.first().name,
          duplicateIds = cluster.drop(1).map { it.id },
        )
      }
  }

  private fun clusterByIdentity(holdings: List<EtfHolding>): List<List<EtfHolding>> {
    val clusters = mutableListOf<MutableList<EtfHolding>>()
    holdings.forEach { holding ->
      val cluster = clusters.firstOrNull { sameCompany(it.first(), holding) }
      if (cluster != null) cluster.add(holding) else clusters.add(mutableListOf(holding))
    }
    return clusters
  }

  private fun sameCompany(
    representative: EtfHolding,
    candidate: EtfHolding,
  ): Boolean = holdingIdentityService.isSameCompany(representative.name, candidate.name, candidate.ticker ?: representative.ticker)

  private fun logPlan(plan: HoldingMergePlan) {
    log.info(
      "Merge plan: canonical '${LogSanitizerUtil.sanitize(plan.canonicalName)}' (id=${plan.canonicalId}) " +
        "absorbs ${plan.duplicateIds.size} duplicates ${plan.duplicateIds}",
    )
  }
}
