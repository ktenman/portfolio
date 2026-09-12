package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ETF_BREAKDOWN_CACHE
import ee.tenman.portfolio.dto.EtfHoldingBreakdownDto
import ee.tenman.portfolio.model.holding.HoldingKey
import ee.tenman.portfolio.model.holding.HoldingValue
import ee.tenman.portfolio.model.holding.toHoldingData
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class EtfBenchmarkService(
  private val instrumentRepository: InstrumentRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val holdingAggregationService: HoldingAggregationService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(readOnly = true)
  @Cacheable(ETF_BREAKDOWN_CACHE, key = "'benchmark-' + #symbol", unless = "#result.isEmpty()")
  fun getBenchmarkHoldings(symbol: String): List<EtfHoldingBreakdownDto> {
    val benchmark = instrumentRepository.findBySymbol(symbol).orElse(null)
    if (benchmark == null) {
      log.warn("Benchmark instrument not found for symbol ${LogSanitizerUtil.sanitize(symbol)}")
      return emptyList()
    }
    val positions =
      etfPositionRepository
        .findLatestPositionsByEtfId(benchmark.id)
        .filter { it.weightPercentage > BigDecimal.ZERO }
    if (positions.isEmpty()) {
      log.warn("Benchmark ${LogSanitizerUtil.sanitize(symbol)} has no weighted positions to compare against")
      return emptyList()
    }
    val holdings =
      holdingAggregationService.aggregateHoldings(
        positions.map { it.toHoldingData(it.weightPercentage, symbol, emptySet()) },
      )
    val capturedWeight = holdings.values.sumOf { it.totalValue }
    log.info("Benchmark ${LogSanitizerUtil.sanitize(symbol)} captures $capturedWeight% of fund weight across ${holdings.size} holdings")
    return holdings
      .map { (key, value) -> toBreakdownDto(key, value, capturedWeight, symbol) }
      .sortedByDescending { it.percentageOfTotal }
  }

  private fun toBreakdownDto(
    key: HoldingKey,
    value: HoldingValue,
    capturedWeight: BigDecimal,
    symbol: String,
  ) = EtfHoldingBreakdownDto(
    holdingUuid = key.holdingUuid,
    holdingTicker = key.ticker,
    holdingName = key.name,
    percentageOfTotal =
      value.totalValue
        .multiply(BigDecimal(100))
        .divide(capturedWeight, 4, RoundingMode.HALF_UP),
    totalValueEur = BigDecimal.ZERO,
    holdingSector = key.sector,
    holdingIndustry = key.industry,
    holdingCountryCode = key.countryCode,
    holdingCountryName = key.countryName,
    inEtfs = symbol,
    numEtfs = 1,
    platforms = "",
  )
}
