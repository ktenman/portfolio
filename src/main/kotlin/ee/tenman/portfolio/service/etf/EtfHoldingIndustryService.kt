package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.common.orNotFound
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.domain.IndustrySource
import ee.tenman.portfolio.domain.SectorSource
import ee.tenman.portfolio.domain.VanguardIndustryUpdate
import ee.tenman.portfolio.repository.EtfHoldingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class EtfHoldingIndustryService(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(readOnly = true)
  fun findUnclassifiedByIndustry(): List<EtfHolding> = etfHoldingRepository.findUnclassifiedIndustryHoldings(MAX_INDUSTRY_FETCH_ATTEMPTS)

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun updateIndustry(
    holdingId: Long,
    industry: GicsIndustry,
    classifiedByModel: AiModel?,
  ): Boolean {
    val holding = etfHoldingRepository.findByIdForUpdate(holdingId).orNotFound(holdingId)
    if (holding.industry != null) return false
    holding.industry = industry
    holding.industryClassifiedByModel = classifiedByModel
    holding.industrySource = IndustrySource.LLM
    holding.industryEffectiveDate = null
    etfHoldingRepository.save(holding)
    return true
  }

  @Transactional
  fun updateVanguardIndustries(updates: List<VanguardIndustryUpdate>): Int {
    val today = LocalDate.now(clock)
    val selected =
      updates
        .filter { isCurrent(it, today) }
        .groupBy { it.holdingUuid }
        .toSortedMap()
        .values
        .mapNotNull { newest(it) }
    return selected.count { apply(it) }
  }

  @Transactional
  fun inheritVanguardIndustry(
    canonical: EtfHolding,
    duplicates: List<EtfHolding>,
  ) {
    val updates =
      duplicates.filter { it.industrySource == IndustrySource.VANGUARD }.mapNotNull {
        val industry = it.industry ?: return@mapNotNull null
        val date = it.industryEffectiveDate ?: return@mapNotNull null
        VanguardIndustryUpdate(canonical.uuid, industry, date)
      }
    if (updates.isEmpty()) return
    val update = newest(updates) ?: return
    replace(canonical, update)
  }

  private fun isCurrent(
    update: VanguardIndustryUpdate,
    today: LocalDate,
  ): Boolean {
    if (update.effectiveDate in today.minusMonths(2)..today) return true
    log.warn("Skipping Vanguard industry for ${update.holdingUuid} with invalid effective date ${update.effectiveDate}")
    return false
  }

  private fun newest(updates: List<VanguardIndustryUpdate>): VanguardIndustryUpdate? {
    val date = updates.maxOf { it.effectiveDate }
    val latest = updates.filter { it.effectiveDate == date }.distinctBy { it.industry }
    if (latest.size == 1) return latest.single()
    log.warn("Quarantining conflicting Vanguard industries for ${latest.first().holdingUuid} on $date: ${latest.map { it.industry }}")
    return null
  }

  private fun apply(update: VanguardIndustryUpdate): Boolean {
    val holding = etfHoldingRepository.findByUuidForUpdate(update.holdingUuid)
    if (holding == null) {
      log.warn("Skipping Vanguard industry for unresolved holding UUID ${update.holdingUuid}")
      return false
    }
    return replace(holding, update)
  }

  private fun replace(
    holding: EtfHolding,
    update: VanguardIndustryUpdate,
  ): Boolean {
    if (!accepts(holding, update) || matches(holding, update)) return false
    if (holding.industry != update.industry && holding.sectorSource == SectorSource.INDUSTRY) holding.sector = null
    holding.industry = update.industry
    holding.industrySource = IndustrySource.VANGUARD
    holding.industryEffectiveDate = update.effectiveDate
    holding.industryClassifiedByModel = null
    holding.deriveSector()
    etfHoldingRepository.save(holding)
    return true
  }

  private fun accepts(
    holding: EtfHolding,
    update: VanguardIndustryUpdate,
  ): Boolean {
    if (holding.industrySource == IndustrySource.VANGUARD) return acceptsDate(holding, update)
    if (holding.industry == null || holding.industry == update.industry || holding.industrySource == IndustrySource.LLM) return true
    log.warn("Protecting industry ${holding.industry} with unknown source for ${holding.uuid} against Vanguard ${update.industry}")
    return false
  }

  private fun acceptsDate(
    holding: EtfHolding,
    update: VanguardIndustryUpdate,
  ): Boolean {
    val date = holding.industryEffectiveDate ?: return true
    if (update.effectiveDate < date) {
      log.warn("Skipping older Vanguard industry for ${holding.uuid} on ${update.effectiveDate} because $date is already stored")
      return false
    }
    if (update.effectiveDate != date || holding.industry == update.industry) return true
    log.warn("Quarantining conflicting Vanguard industry for ${holding.uuid} on $date: ${holding.industry} versus ${update.industry}")
    return false
  }

  private fun matches(
    holding: EtfHolding,
    update: VanguardIndustryUpdate,
  ): Boolean =
    holding.industry == update.industry &&
      holding.industrySource == IndustrySource.VANGUARD &&
      holding.industryEffectiveDate == update.effectiveDate &&
    holding.industryClassifiedByModel == null

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun incrementIndustryFetchAttempts(holdingId: Long) {
    val holding = etfHoldingRepository.findById(holdingId).orNotFound(holdingId)
    holding.industryFetchAttempts++
    etfHoldingRepository.save(holding)
    log.info("Incremented industry fetch attempts for holding id=$holdingId to ${holding.industryFetchAttempts}")
  }

  @Transactional
  fun deriveMissingSectors(): Int = etfHoldingRepository.findBySectorIsNullAndIndustryIsNotNull().onEach { it.deriveSector() }.size

  companion object {
    const val MAX_INDUSTRY_FETCH_ATTEMPTS = 3
  }
}
