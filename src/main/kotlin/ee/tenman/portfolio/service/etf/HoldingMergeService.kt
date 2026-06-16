package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.common.orNotFound
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class HoldingMergeService(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val etfPositionRepository: EtfPositionRepository,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun merge(
    canonicalId: Long,
    duplicateIds: List<Long>,
  ) {
    if (duplicateIds.isEmpty()) return
    val canonical = etfHoldingRepository.findById(canonicalId).orNotFound(canonicalId)
    val duplicates = etfHoldingRepository.findAllById(duplicateIds).sortedBy { it.id }
    repointPositions(canonical, duplicates)
    duplicates.forEach { etfHoldingRepository.delete(it) }
    etfHoldingRepository.flush()
    duplicates.forEach { applyMissingFields(canonical, it) }
    etfHoldingRepository.save(canonical)
    log.info("Merged ${duplicates.size} duplicate holdings into canonical id=$canonicalId")
  }

  private fun repointPositions(
    canonical: EtfHolding,
    duplicates: List<EtfHolding>,
  ) {
    val retainedWeights =
      etfPositionRepository
        .findByHoldingId(canonical.id)
        .associateTo(mutableMapOf()) { (it.etfInstrument.id to it.snapshotDate) to it.weightPercentage }
    val positionsByDuplicate = etfPositionRepository.findByHoldingIdIn(duplicates.map { it.id }).groupBy { it.holding.id }
    duplicates.forEach { duplicate ->
      positionsByDuplicate[duplicate.id].orEmpty().forEach { position ->
        val key = position.etfInstrument.id to position.snapshotDate
        val retained = retainedWeights.putIfAbsent(key, position.weightPercentage)
        if (retained != null) return@forEach discardCollidingPosition(position, retained)
        position.holding = canonical
      }
    }
  }

  private fun discardCollidingPosition(
    position: EtfPosition,
    retainedWeight: BigDecimal,
  ) {
    if (position.weightPercentage.compareTo(retainedWeight) != 0) {
      log.warn(
        "Discarding colliding holding position weight=${position.weightPercentage} keeping=$retainedWeight " +
          "for etf=${position.etfInstrument.id} date=${position.snapshotDate}",
      )
    }
    etfPositionRepository.delete(position)
  }

  private fun applyMissingFields(
    canonical: EtfHolding,
    duplicate: EtfHolding,
  ) {
    applyMissingTicker(canonical, duplicate)
    applyMissingSector(canonical, duplicate)
    applyMissingCountry(canonical, duplicate)
  }

  private fun applyMissingTicker(
    canonical: EtfHolding,
    duplicate: EtfHolding,
  ) {
    if (!canonical.ticker.isNullOrBlank()) return
    if (duplicate.ticker.isNullOrBlank()) return
    canonical.ticker = duplicate.ticker
  }

  private fun applyMissingSector(
    canonical: EtfHolding,
    duplicate: EtfHolding,
  ) {
    if (canonical.sector != null) return
    if (duplicate.sector == null) return
    canonical.sector = duplicate.sector
    canonical.sectorSource = duplicate.sectorSource
    canonical.classifiedByModel = duplicate.classifiedByModel
  }

  private fun applyMissingCountry(
    canonical: EtfHolding,
    duplicate: EtfHolding,
  ) {
    if (!canonical.countryCode.isNullOrBlank()) return
    if (duplicate.countryCode.isNullOrBlank()) return
    canonical.countryCode = duplicate.countryCode
    canonical.countryName = duplicate.countryName
    canonical.countryClassifiedByModel = duplicate.countryClassifiedByModel
  }
}
