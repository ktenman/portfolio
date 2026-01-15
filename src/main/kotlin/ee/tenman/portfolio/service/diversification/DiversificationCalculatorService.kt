package ee.tenman.portfolio.service.diversification

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.DIVERSIFICATION_ETFS_CACHE
import ee.tenman.portfolio.dto.AllocationDto
import ee.tenman.portfolio.dto.ConcentrationDto
import ee.tenman.portfolio.dto.DiversificationCalculatorRequestDto
import ee.tenman.portfolio.dto.DiversificationCalculatorResponseDto
import ee.tenman.portfolio.dto.DiversificationCountryDto
import ee.tenman.portfolio.dto.DiversificationHoldingDto
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
    val positions = etfPositionRepository.findLatestPositionsByEtfIds(instrumentIds)
    val positionsByEtfId = positions.groupBy { it.etfInstrument.id }
    val etfDetails = buildEtfDetails(allocations, instruments)
    val weightedTer = calculateWeightedTer(etfDetails)
    val weightedAnnualReturn = calculateWeightedAnnualReturn(etfDetails)
    val holdingsData = aggregateHoldings(allocations, positionsByEtfId, instruments)
    val holdings = buildHoldingDtos(holdingsData)
    val sectors = aggregateSectors(holdingsData)
    val countries = aggregateCountries(holdingsData)
    val concentration = buildConcentration(holdings)
    return DiversificationCalculatorResponseDto(
      weightedTer = weightedTer,
      weightedAnnualReturn = weightedAnnualReturn,
      totalUniqueHoldings = holdings.size,
      etfDetails = etfDetails,
      holdings = holdings,
      sectors = sectors,
      countries = countries,
      concentration = concentration,
    )
  }

  @Transactional(readOnly = true)
  @Cacheable(value = [DIVERSIFICATION_ETFS_CACHE], key = "'available-etfs'", unless = "#result.isEmpty()")
  fun getAvailableEtfs(): List<EtfDetailDto> {
    val etfIds = etfPositionRepository.findDistinctEtfInstrumentIds()
    val instruments = instrumentRepository.findAllById(etfIds)
    return instruments
      .map { instrument ->
        EtfDetailDto(
          instrumentId = instrument.id,
          symbol = instrument.symbol,
          name = instrument.name,
          allocation = BigDecimal.ZERO,
          ter = instrument.ter,
          annualReturn = instrument.xirrAnnualReturn,
          currentPrice = instrument.currentPrice,
        )
      }.sortedBy { it.symbol }
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
    instruments: Map<Long, ee.tenman.portfolio.domain.Instrument>,
  ): List<EtfDetailDto> =
    allocations.mapNotNull { allocation ->
      val instrument = instruments[allocation.instrumentId] ?: return@mapNotNull null
      EtfDetailDto(
        instrumentId = instrument.id,
        symbol = instrument.symbol,
        name = instrument.name,
        allocation = allocation.percentage,
        ter = instrument.ter,
        annualReturn = instrument.xirrAnnualReturn,
        currentPrice = instrument.currentPrice,
      )
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
    positionsByEtfId: Map<Long, List<ee.tenman.portfolio.domain.EtfPosition>>,
    instruments: Map<Long, ee.tenman.portfolio.domain.Instrument>,
  ): Map<String, AggregatedHolding> =
    allocations.fold(emptyMap()) { acc, allocation ->
      val positions = positionsByEtfId[allocation.instrumentId] ?: return@fold acc
      val instrument = instruments[allocation.instrumentId] ?: return@fold acc
      positions.fold(acc) { innerAcc, position ->
        val key = normalizeHoldingName(position.holding.name)
        val weightedPercentage =
          position.weightPercentage
            .multiply(allocation.percentage)
            .divide(HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP)
        val existing = innerAcc[key]
        val updated =
          if (existing != null) {
            existing.copy(
              percentage = existing.percentage.add(weightedPercentage),
              etfSymbols = existing.etfSymbols + instrument.symbol,
            )
          } else {
            AggregatedHolding(
              name = position.holding.name,
              ticker = position.holding.ticker,
              sector = position.holding.sector,
              countryCode = position.holding.countryCode,
              countryName = position.holding.countryName,
              percentage = weightedPercentage,
              etfSymbols = setOf(instrument.symbol),
            )
          }
        innerAcc + (key to updated)
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
    private val HUNDRED = BigDecimal(100)
  }
}
