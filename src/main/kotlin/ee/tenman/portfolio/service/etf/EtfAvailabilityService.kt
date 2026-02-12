package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.AvailableEtfsDto
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.specification.InstrumentSpecifications
import ee.tenman.portfolio.service.transaction.TransactionCalculationService
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class EtfAvailabilityService(
  private val instrumentRepository: InstrumentRepository,
  private val transactionCalculationService: TransactionCalculationService,
  private val syntheticEtfCalculationService: SyntheticEtfCalculationService,
) {
  @Transactional(readOnly = true)
  fun getAvailableEtfs(platforms: List<String>?): AvailableEtfsDto {
    val platformFilter = parsePlatforms(platforms)
    val instruments = instrumentRepository.findAll(buildSpecification(platformFilter))
    if (instruments.isEmpty()) return AvailableEtfsDto(emptyList(), emptyList())
    val (syntheticInstruments, regularInstruments) = instruments.partition { it.providerName == ProviderName.SYNTHETIC }
    val transactionData = transactionCalculationService.batchCalculateAll(regularInstruments.map { it.id }, platformFilter)
    val activeRegular = regularInstruments.filter { hasPositiveQuantity(transactionData[it.id]?.netQuantity) }
    val activeSynthetic = syntheticInstruments.filter { syntheticEtfCalculationService.hasActiveHoldings(it.id) }
    val activePlatforms = transactionData.values.flatMapTo(mutableSetOf()) { it.platforms }
    return AvailableEtfsDto(
      etfSymbols = (activeRegular + activeSynthetic).map { it.symbol }.sorted(),
      platforms = activePlatforms.map { it.name }.sorted(),
    )
  }

  private fun buildSpecification(platformFilter: Set<Platform>?): Specification<Instrument> {
    val baseSpec = InstrumentSpecifications.hasProviderNameIn(ETF_PROVIDERS)
    if (platformFilter == null) return baseSpec
    return baseSpec
      .and(InstrumentSpecifications.hasTransactionsOnPlatforms(platformFilter))
      .or(InstrumentSpecifications.hasProviderNameIn(listOf(ProviderName.SYNTHETIC)))
  }

  private fun hasPositiveQuantity(quantity: BigDecimal?): Boolean = quantity != null && quantity > BigDecimal.ZERO

  private fun parsePlatforms(platforms: List<String>?): Set<Platform>? {
    if (platforms.isNullOrEmpty()) return null
    return platforms.mapNotNull { Platform.fromStringOrNull(it) }.toSet().takeIf { it.isNotEmpty() }
  }

  companion object {
    private val ETF_PROVIDERS = listOf(ProviderName.LIGHTYEAR, ProviderName.FT, ProviderName.SYNTHETIC)
  }
}
