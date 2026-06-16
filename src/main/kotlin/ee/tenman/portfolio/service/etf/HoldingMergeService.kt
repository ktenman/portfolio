package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.common.orNotFound
import ee.tenman.portfolio.common.orNull
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
    val duplicates = duplicateIds.mapNotNull { etfHoldingRepository.findById(it).orNull() }
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
    val owned =
      etfPositionRepository
        .findByHoldingId(canonical.id)
        .map { it.etfInstrument.id to it.snapshotDate }
        .toMutableSet()
    duplicates.forEach { duplicate ->
      etfPositionRepository.findByHoldingId(duplicate.id).forEach { position ->
        val key = position.etfInstrument.id to position.snapshotDate
        if (owned.add(key)) {
          position.holding = canonical
          etfPositionRepository.save(position)
        } else {
          etfPositionRepository.delete(position)
        }
      }
    }
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
