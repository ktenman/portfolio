package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.common.orNotFound
import ee.tenman.portfolio.domain.CountrySource
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.IndustrySource
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HoldingMergeService(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val etfHoldingIndustryService: EtfHoldingIndustryService,
  private val etfHoldingCountryService: EtfHoldingCountryService,
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
    etfHoldingIndustryService.inheritVanguardIndustry(canonical, duplicates)
    etfHoldingCountryService.inheritVanguardCountry(canonical, duplicates)
    etfHoldingRepository.save(canonical)
    log.info("Merged ${duplicates.size} duplicate holdings into canonical id=$canonicalId")
  }

  private fun repointPositions(
    canonical: EtfHolding,
    duplicates: List<EtfHolding>,
  ) {
    val retained =
      etfPositionRepository
        .findByHoldingId(canonical.id)
        .associateByTo(mutableMapOf()) { it.etfInstrument.id to it.snapshotDate }
    val positionsByDuplicate = etfPositionRepository.findByHoldingIdIn(duplicates.map { it.id }).groupBy { it.holding.id }
    duplicates.forEach { duplicate ->
      positionsByDuplicate[duplicate.id].orEmpty().forEach { position ->
        val kept = retained.putIfAbsent(position.etfInstrument.id to position.snapshotDate, position)
        if (kept != null) return@forEach absorb(kept, position)
        position.holding = canonical
      }
    }
  }

  private fun absorb(
    kept: EtfPosition,
    position: EtfPosition,
  ) {
    kept.weightPercentage = kept.weightPercentage.add(position.weightPercentage)
    kept.positionRank = listOfNotNull(kept.positionRank, position.positionRank).minOrNull()
    etfPositionRepository.delete(position)
  }

  private fun applyMissingFields(
    canonical: EtfHolding,
    duplicate: EtfHolding,
  ) {
    applyMissingTicker(canonical, duplicate)
    applySector(canonical, duplicate)
    applyMissingCountry(canonical, duplicate)
    applyMissingIndustry(canonical, duplicate)
  }

  private fun applyMissingTicker(
    canonical: EtfHolding,
    duplicate: EtfHolding,
  ) {
    if (!canonical.ticker.isNullOrBlank()) return
    if (duplicate.ticker.isNullOrBlank()) return
    canonical.ticker = duplicate.ticker
  }

  private fun applySector(
    canonical: EtfHolding,
    duplicate: EtfHolding,
  ) {
    if (duplicate.sector == null) return
    if (!canonical.acceptsSectorFrom(duplicate.sectorSource)) return
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
    if (duplicate.countrySource == CountrySource.VANGUARD) return
    canonical.countryCode = duplicate.countryCode
    canonical.countryName = duplicate.countryName
    canonical.countryClassifiedByModel = duplicate.countryClassifiedByModel
    canonical.countrySource = duplicate.countrySource
    canonical.countryEffectiveDate = duplicate.countryEffectiveDate
  }

  private fun applyMissingIndustry(
    canonical: EtfHolding,
    duplicate: EtfHolding,
  ) {
    if (canonical.industry != null) return
    if (duplicate.industry == null) return
    if (duplicate.industrySource == IndustrySource.VANGUARD) return
    canonical.industry = duplicate.industry
    canonical.industryClassifiedByModel = duplicate.industryClassifiedByModel
    canonical.industrySource = duplicate.industrySource
  }
}
