package ee.tenman.portfolio.service.diversification

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.DIVERSIFICATION_ETFS_CACHE
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.AllocationDto
import ee.tenman.portfolio.dto.ConcentrationDto
import ee.tenman.portfolio.dto.DiversificationCalculatorRequestDto
import ee.tenman.portfolio.dto.DiversificationCalculatorResponseDto
import ee.tenman.portfolio.dto.DiversificationCountryDto
import ee.tenman.portfolio.dto.DiversificationHoldingDto
import ee.tenman.portfolio.dto.DiversificationIndustryDto
import ee.tenman.portfolio.dto.DiversificationSectorDto
import ee.tenman.portfolio.dto.EtfDetailDto
import ee.tenman.portfolio.dto.LargestPositionDto
import ee.tenman.portfolio.model.diversification.AggregatedHolding
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class DiversificationCalculatorService(
  private val instrumentRepository: InstrumentRepository,
  private val etfPositionRepository: EtfPositionRepository,
) {
  @Transactional(readOnly = true)
  fun calculate(request: DiversificationCalculatorRequestDto): DiversificationCalculatorResponseDto {
    validateRequest(request)
    val allocations = normalizeAllocations(request.allocations)
    val instrumentIds = allocations.map { it.instrumentId }
    val instruments = instrumentRepository.findAllById(instrumentIds).associateBy { it.id }
    val weights = holdingWeights(instrumentIds, instruments)
    val etfDetails = buildEtfDetails(allocations, instruments)
    val weightedTer = calculateWeightedTer(etfDetails)
    val weightedAnnualReturn = calculateWeightedAnnualReturn(etfDetails)
    val holdingsData = aggregateHoldings(allocations, weights, instruments)
    val holdings = buildHoldingDtos(holdingsData)
    val sectors = aggregateSectors(holdingsData)
    val industries = aggregateIndustries(holdingsData)
    val countries = aggregateCountries(holdingsData)
    val concentration = buildConcentration(holdings)
    return DiversificationCalculatorResponseDto(
      weightedTer = weightedTer,
      weightedAnnualReturn = weightedAnnualReturn,
      totalUniqueHoldings = holdings.size,
      holdings = holdings,
      sectors = sectors,
      industries = industries,
      countries = countries,
      concentration = concentration,
    )
  }

  @Transactional(readOnly = true)
  @Cacheable(value = [DIVERSIFICATION_ETFS_CACHE], key = "'available-etfs'")
  fun getAvailableEtfs(): List<EtfDetailDto> {
    val etfs =
      instrumentRepository
        .findAllById(etfPositionRepository.findDistinctEtfInstrumentIds())
        .filter { it.category == ETF_CATEGORY && !it.isSynthetic() }
        .map { detail(it) }
    return (etfs + constituents()).distinctBy { it.instrumentId }.sortedBy { it.symbol }
  }

  private fun constituents(): List<EtfDetailDto> {
    val holdings = syntheticHoldings()
    if (holdings.isEmpty()) return emptyList()
    return instrumentRepository.findBySymbolIn(holdings.keys.toList()).map { detail(it, holdings.getValue(it.symbol).name) }
  }

  private fun syntheticHoldings(): Map<String, EtfHolding> {
    val funds = instrumentRepository.findByProviderName(ProviderName.SYNTHETIC)
    if (funds.isEmpty()) return emptyMap()
    return etfPositionRepository
      .findLatestPositionsByEtfIds(funds.map { it.id })
      .mapNotNull { position -> position.holding.ticker?.let { it to position.holding } }
      .toMap()
  }

  private fun detail(
    instrument: Instrument,
    name: String = instrument.name,
    allocation: BigDecimal = BigDecimal.ZERO,
  ): EtfDetailDto =
    EtfDetailDto(
      instrumentId = instrument.id,
      symbol = instrument.symbol,
      name = name,
      allocation = allocation,
      ter = instrument.ter,
      annualReturn = instrument.xirrAnnualReturn,
      currentPrice = instrument.currentPrice,
      fundCurrency = instrument.fundCurrency,
    )

  private fun holdingWeights(
    instrumentIds: List<Long>,
    instruments: Map<Long, Instrument>,
  ): Map<Long, List<Pair<EtfHolding, BigDecimal>>> {
    val own =
      etfPositionRepository
        .findLatestPositionsByEtfIds(instrumentIds)
        .groupBy({ it.etfInstrument.id }, { it.holding to it.weightPercentage })
    val coins = syntheticHoldings()
    val single = instruments.values.mapNotNull { instrument -> coins[instrument.symbol]?.let { instrument.id to listOf(it to HUNDRED) } }
    return single.toMap() + own
  }

  private fun validateRequest(request: DiversificationCalculatorRequestDto) {
    val validAllocations = request.allocations.filter { it.instrumentId > 0 }
    require(validAllocations.isNotEmpty()) { "At least one valid ETF allocation is required" }
    validAllocations.forEach { allocation ->
      require(allocation.percentage >= BigDecimal.ZERO) { "Allocation percentage cannot be negative" }
    }
    val totalPercentage = validAllocations.sumOf { it.percentage }
    require(totalPercentage > BigDecimal.ZERO) { "Total allocation percentage must be greater than zero" }
  }

  private fun normalizeAllocations(allocations: List<AllocationDto>): List<AllocationDto> {
    val validAllocations = allocations.filter { it.instrumentId > 0 && it.percentage > BigDecimal.ZERO }
    val total = validAllocations.sumOf { it.percentage }
    if (total.signum() == 0) return validAllocations
    return validAllocations.map { a ->
      AllocationDto(
        instrumentId = a.instrumentId,
        percentage = a.percentage.multiply(HUNDRED).divide(total, CALCULATION_SCALE, RoundingMode.HALF_UP),
      )
    }
  }

  private fun buildEtfDetails(
    allocations: List<AllocationDto>,
    instruments: Map<Long, Instrument>,
  ): List<EtfDetailDto> =
    allocations.mapNotNull { allocation ->
      instruments[allocation.instrumentId]?.let { detail(it, allocation = allocation.percentage) }
    }

  private fun calculateWeightedTer(etfDetails: List<EtfDetailDto>): BigDecimal = calculateWeightedValue(etfDetails) { it.ter }

  private fun calculateWeightedAnnualReturn(etfDetails: List<EtfDetailDto>): BigDecimal =
    calculateWeightedValue(etfDetails) { it.annualReturn }

  private fun calculateWeightedValue(
    etfDetails: List<EtfDetailDto>,
    valueSelector: (EtfDetailDto) -> BigDecimal?,
  ): BigDecimal {
    val withValue = etfDetails.mapNotNull { etf -> valueSelector(etf)?.let { etf to it } }
    if (withValue.isEmpty()) return BigDecimal.ZERO
    val weightedSum =
      withValue.sumOf { (etf, value) ->
      etf.allocation.multiply(value).divide(HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP)
    }
    val totalAllocation = withValue.sumOf { (etf, _) -> etf.allocation }
    if (totalAllocation.signum() == 0) return BigDecimal.ZERO
    return weightedSum.multiply(HUNDRED).divide(totalAllocation, RESULT_SCALE, RoundingMode.HALF_UP)
  }

  private fun aggregateHoldings(
    allocations: List<AllocationDto>,
    weights: Map<Long, List<Pair<EtfHolding, BigDecimal>>>,
    instruments: Map<Long, Instrument>,
  ): Map<String, AggregatedHolding> =
    allocations
      .flatMap { allocation -> weightedHoldings(allocation, weights, instruments) }
      .groupBy { normalizeHoldingName(it.name) }
      .mapValues { (_, holdings) ->
        holdings.reduce { first, next ->
          first.copy(percentage = first.percentage.add(next.percentage), etfSymbols = first.etfSymbols + next.etfSymbols)
        }
      }

  private fun weightedHoldings(
    allocation: AllocationDto,
    weights: Map<Long, List<Pair<EtfHolding, BigDecimal>>>,
    instruments: Map<Long, Instrument>,
  ): List<AggregatedHolding> {
    val instrument = instruments[allocation.instrumentId] ?: return emptyList()
    return weights[allocation.instrumentId].orEmpty().map { (holding, weight) ->
      AggregatedHolding(
        name = holding.name,
        ticker = holding.ticker,
        sector = holding.sector?.displayName,
        industry = resolveIndustry(holding),
        countryCode = holding.countryCode,
        countryName = holding.countryName,
        percentage = weight.multiply(allocation.percentage).divide(HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP),
        etfSymbols = setOf(instrument.symbol),
      )
    }
  }

  private fun normalizeHoldingName(name: String): String = name.lowercase().replace(Regex("\\s+"), " ").trim()

  private fun buildHoldingDtos(holdingsData: Map<String, AggregatedHolding>): List<DiversificationHoldingDto> =
    holdingsData.values
      .filter { it.percentage.signum() > 0 }
      .map { holding ->
        DiversificationHoldingDto(
          name = holding.name,
          ticker = holding.ticker,
          percentage = holding.percentage.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
          inEtfs = holding.etfSymbols.sorted().joinToString(", "),
        )
      }.sortedByDescending { it.percentage }

  private fun aggregateSectors(holdingsData: Map<String, AggregatedHolding>): List<DiversificationSectorDto> =
    holdingsData.values
      .groupBy { it.sector ?: UNKNOWN }
      .map { (sector, holdings) ->
        DiversificationSectorDto(
          sector = sector,
          percentage = holdings.sumOf { it.percentage }.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
        )
      }.filter { it.percentage.signum() > 0 }
      .sortedByDescending { it.percentage }

  private fun resolveIndustry(holding: EtfHolding): String? {
    if (holding.sector == IndustrySector.CRYPTOCURRENCY) return IndustrySector.CRYPTOCURRENCY.displayName
    return holding.industry?.displayName
  }

  private fun aggregateIndustries(holdingsData: Map<String, AggregatedHolding>): List<DiversificationIndustryDto> =
    holdingsData.values
      .groupBy { it.industry ?: UNCLASSIFIED }
      .map { (industry, holdings) ->
        DiversificationIndustryDto(
          industry = industry,
          percentage = holdings.sumOf { it.percentage }.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
        )
      }.filter { it.percentage.signum() > 0 }
      .sortedByDescending { it.percentage }

  private fun aggregateCountries(holdingsData: Map<String, AggregatedHolding>): List<DiversificationCountryDto> =
    holdingsData.values
      .groupBy { it.countryName ?: UNKNOWN }
      .map { (countryName, holdings) ->
        DiversificationCountryDto(
          countryCode = holdings.firstNotNullOfOrNull { it.countryCode },
          countryName = countryName,
          percentage = holdings.sumOf { it.percentage }.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
        )
      }.filter { it.percentage.signum() > 0 }
      .sortedByDescending { it.percentage }

  private fun buildConcentration(holdings: List<DiversificationHoldingDto>): ConcentrationDto {
    val top10Percentage = holdings.take(TOP_HOLDINGS_COUNT).sumOf { it.percentage }
    return ConcentrationDto(
      top10Percentage = top10Percentage.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
      largestPosition = holdings.firstOrNull()?.let { LargestPositionDto(it.name, it.percentage) },
    )
  }

  companion object {
    private const val CALCULATION_SCALE = 10
    private const val RESULT_SCALE = 4
    private const val TOP_HOLDINGS_COUNT = 10
    private const val UNKNOWN = "Unknown"
    private const val UNCLASSIFIED = "Unclassified"
    private const val ETF_CATEGORY = "ETF"
    private val HUNDRED = BigDecimal(100)
  }
}
