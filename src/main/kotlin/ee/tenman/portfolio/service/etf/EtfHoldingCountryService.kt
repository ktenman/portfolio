package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.common.orNotFound
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.CountrySource
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.VanguardCountryUpdate
import ee.tenman.portfolio.repository.EtfHoldingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.Locale

@Service
class EtfHoldingCountryService(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun updateCountry(
    holdingId: Long,
    countryCode: String,
    countryName: String,
    classifiedByModel: AiModel?,
  ): Boolean {
    val holding = etfHoldingRepository.findByIdForUpdate(holdingId).orNotFound(holdingId)
    if (!holding.countryCode.isNullOrBlank()) return false
    holding.countryCode = countryCode
    holding.countryName = countryName
    holding.countryClassifiedByModel = classifiedByModel
    holding.countrySource = if (classifiedByModel == null) CountrySource.RULE else CountrySource.LLM
    holding.countryEffectiveDate = null
    etfHoldingRepository.save(holding)
    return true
  }

  @Transactional
  fun updateVanguardCountries(updates: List<VanguardCountryUpdate>): Int {
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
  fun inheritVanguardCountry(
    canonical: EtfHolding,
    duplicates: List<EtfHolding>,
  ) {
    val updates =
      duplicates.filter { it.countrySource == CountrySource.VANGUARD }.mapNotNull {
        val code = it.countryCode?.takeIf { code -> code in COUNTRY_CODES } ?: return@mapNotNull null
        val date = it.countryEffectiveDate ?: return@mapNotNull null
        VanguardCountryUpdate(canonical.uuid, code, date)
      }
    if (updates.isEmpty()) return
    val update = newest(updates) ?: return
    replace(canonical, update)
  }

  private fun isCurrent(
    update: VanguardCountryUpdate,
    today: LocalDate,
  ): Boolean {
    if (update.countryCode in COUNTRY_CODES && update.effectiveDate in today.minusMonths(2)..today) return true
    log.warn("Skipping Vanguard country ${update.countryCode} for ${update.holdingUuid} with effective date ${update.effectiveDate}")
    return false
  }

  private fun newest(updates: List<VanguardCountryUpdate>): VanguardCountryUpdate? {
    val date = updates.maxOf { it.effectiveDate }
    val latest = updates.filter { it.effectiveDate == date }.distinctBy { it.countryCode }
    if (latest.size == 1) return latest.single()
    log.warn("Quarantining conflicting Vanguard countries for ${latest.first().holdingUuid} on $date: ${latest.map { it.countryCode }}")
    return null
  }

  private fun apply(update: VanguardCountryUpdate): Boolean {
    val holding = etfHoldingRepository.findByUuidForUpdate(update.holdingUuid)
    if (holding == null) {
      log.warn("Skipping Vanguard country for unresolved holding UUID ${update.holdingUuid}")
      return false
    }
    return replace(holding, update)
  }

  private fun replace(
    holding: EtfHolding,
    update: VanguardCountryUpdate,
  ): Boolean {
    if (!accepts(holding, update) || matches(holding, update)) return false
    holding.countryCode = update.countryCode
    holding.countryName = countryName(update.countryCode)
    holding.countrySource = CountrySource.VANGUARD
    holding.countryEffectiveDate = update.effectiveDate
    holding.countryClassifiedByModel = null
    etfHoldingRepository.save(holding)
    return true
  }

  private fun accepts(
    holding: EtfHolding,
    update: VanguardCountryUpdate,
  ): Boolean {
    if (holding.countrySource != CountrySource.VANGUARD) return true
    val date = holding.countryEffectiveDate ?: return true
    if (update.effectiveDate < date) return false
    if (update.effectiveDate != date || holding.countryCode == update.countryCode) return true
    log.warn("Quarantining conflicting Vanguard country for ${holding.uuid} on $date: ${holding.countryCode} versus ${update.countryCode}")
    return false
  }

  private fun matches(
    holding: EtfHolding,
    update: VanguardCountryUpdate,
  ): Boolean =
    holding.countryCode == update.countryCode &&
      holding.countryName == countryName(update.countryCode) &&
      holding.countrySource == CountrySource.VANGUARD &&
      holding.countryEffectiveDate == update.effectiveDate &&
      holding.countryClassifiedByModel == null

  private fun countryName(code: String): String = Locale.of("", code).getDisplayCountry(Locale.ENGLISH)

  companion object {
    private val COUNTRY_CODES = Locale.getISOCountries().toSet()
  }
}
