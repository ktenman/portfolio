package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.AvailableEtfsDto
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.specification.InstrumentSpecifications
import ee.tenman.portfolio.repository.specification.InstrumentSpecifications.ETF_PROVIDERS
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
    val platformFilter = Platform.parseFrom(platforms)
    val instruments = instrumentRepository.findAll(buildSpecification(platformFilter))
    if (instruments.isEmpty()) return AvailableEtfsDto(emptyList(), emptyList())
    val (syntheticInstruments, regularInstruments) = instruments.partition { it.providerName == ProviderName.SYNTHETIC }
    val transactionData = transactionCalculationService.batchCalculateAll(regularInstruments.map { it.id }, platformFilter)
    val activeRegular = regularInstruments.filter { transactionData[it.id]?.netQuantity?.let { qty -> qty > BigDecimal.ZERO } == true }
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
    return baseSpec.and(
      InstrumentSpecifications
        .hasTransactionsOnPlatforms(platformFilter)
        .or(InstrumentSpecifications.hasProviderNameIn(listOf(ProviderName.SYNTHETIC))),
    )
  }
}
