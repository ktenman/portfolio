package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.dto.EtfHoldingBreakdownDto
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class EtfBreakdownService(
  private val instrumentRepository: InstrumentRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val transactionRepository: PortfolioTransactionRepository,
  private val dailyPriceService: DailyPriceService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  data class HoldingKey(
    val ticker: String?,
    val name: String,
    val sector: String?,
  )

  data class HoldingValue(
    val totalValue: BigDecimal,
    val etfSymbols: MutableSet<String>,
  )

  @Cacheable(
    "etf:breakdown",
    key = "#etfSymbols != null && !#etfSymbols.isEmpty() ? new java.util.TreeSet(#etfSymbols).toString() : 'all'",
    unless = "#result.isEmpty()",
  )
  fun getHoldingsBreakdown(etfSymbols: List<String>? = null): List<EtfHoldingBreakdownDto> {
    val lightyearEtfs = getLightyearEtfs(etfSymbols)
    log.info("Found ${lightyearEtfs.size} ETFs: ${lightyearEtfs.map { it.symbol }}")

    if (lightyearEtfs.isEmpty()) {
      log.warn("No Lightyear ETFs found")
      return emptyList()
    }

    val actualPortfolioTotal = calculateActualPortfolioTotal(lightyearEtfs)
    log.info("Actual portfolio total value from transactions: $actualPortfolioTotal")

    val holdingsMap = buildHoldingsMap(lightyearEtfs)
    log.info("Built holdings map with ${holdingsMap.size} unique holdings")

    val result = aggregateByHolding(holdingsMap, actualPortfolioTotal)
    log.info("Returning ${result.size} holdings in breakdown")
    return result
  }

  @CacheEvict("etf:breakdown", allEntries = true)
  fun evictBreakdownCache() {
    log.info("Evicting ETF breakdown cache")
  }

  private fun getLightyearEtfs(etfSymbols: List<String>? = null): List<Instrument> {
    val lightyearInstruments = instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR)
    val ftInstruments = instrumentRepository.findByProviderName(ProviderName.FT)

    log.info("Found ${lightyearInstruments.size} LIGHTYEAR instruments and ${ftInstruments.size} FT instruments")

    var allInstruments = lightyearInstruments + ftInstruments

    if (!etfSymbols.isNullOrEmpty()) {
      allInstruments = allInstruments.filter { it.symbol in etfSymbols }
      log.info("Filtered to ${allInstruments.size} instruments matching symbols: {}", LogSanitizerUtil.sanitize(etfSymbols))
    }

    val withHoldings = allInstruments.filter { hasEtfHoldings(it.id) }
    log.info("${withHoldings.size} instruments have ETF holdings data: ${withHoldings.map { it.symbol }}")

    val withActivePositions = withHoldings.filter { hasActiveHoldings(it.id) }
    log.info("${withActivePositions.size} instruments have active positions: ${withActivePositions.map { it.symbol }}")

    return withActivePositions
  }

  private fun hasEtfHoldings(instrumentId: Long): Boolean = etfPositionRepository.findLatestPositionsByEtfId(instrumentId).isNotEmpty()

  private fun hasActiveHoldings(instrumentId: Long): Boolean {
    val netQuantity = calculateNetQuantity(instrumentId)
    log.debug("Instrument $instrumentId has net quantity: $netQuantity")
    return netQuantity > BigDecimal.ZERO
  }

  private fun calculateNetQuantity(instrumentId: Long): BigDecimal {
    val transactions = transactionRepository.findAllByInstrumentId(instrumentId)
    return transactions.fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        ee.tenman.portfolio.domain.TransactionType.BUY -> acc.add(tx.quantity)
        ee.tenman.portfolio.domain.TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }
  }

  private fun buildHoldingsMap(etfs: List<Instrument>): Map<HoldingKey, HoldingValue> {
    val holdingsMap = mutableMapOf<HoldingKey, HoldingValue>()

    etfs.forEach { etf ->
      val positions = etfPositionRepository.findLatestPositionsByEtfId(etf.id)
      val etfQuantity = calculateNetQuantity(etf.id)
      val etfPrice = getCurrentPrice(etf)

      positions.forEach { position ->
          val holdingValue = calculateHoldingValue(position, etfQuantity, etfPrice)
          val key =
            HoldingKey(
              ticker = position.holding.ticker,
              name = position.holding.name,
              sector = position.holding.sector,
            )

          holdingsMap.merge(key, HoldingValue(holdingValue, mutableSetOf(etf.symbol))) { existing, new ->
            HoldingValue(
              totalValue = existing.totalValue.add(new.totalValue),
              etfSymbols = existing.etfSymbols.apply { addAll(new.etfSymbols) },
            )
          }
        }
    }

    return holdingsMap
  }

  private fun getCurrentPrice(instrument: Instrument): BigDecimal {
    val currentPrice = instrument.currentPrice
    if (currentPrice != null && currentPrice > BigDecimal.ZERO) {
      return currentPrice
    }

    return try {
      dailyPriceService.getPrice(instrument, java.time.LocalDate.now())
    } catch (e: NoSuchElementException) {
      log.warn("No price found for ${instrument.symbol}, using zero")
      BigDecimal.ZERO
    }
  }

  private fun calculateHoldingValue(
    position: EtfPosition,
    etfQuantity: BigDecimal,
    etfPrice: BigDecimal,
  ): BigDecimal {
    val etfValue = etfQuantity.multiply(etfPrice)
    val weightDecimal = position.weightPercentage.divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
    return etfValue.multiply(weightDecimal)
  }

  private fun calculateActualPortfolioTotal(etfs: List<Instrument>): BigDecimal =
    etfs.fold(BigDecimal.ZERO) { acc, etf ->
      val quantity = calculateNetQuantity(etf.id)
      val price = getCurrentPrice(etf)
      acc.add(quantity.multiply(price))
    }

  private fun aggregateByHolding(
    holdingsMap: Map<HoldingKey, HoldingValue>,
    portfolioTotal: BigDecimal,
  ): List<EtfHoldingBreakdownDto> {
    if (portfolioTotal == BigDecimal.ZERO) {
      log.warn("Portfolio total is zero, cannot calculate percentages")
      return emptyList()
    }

    val holdingsTotal = holdingsMap.values.fold(BigDecimal.ZERO) { acc, value -> acc.add(value.totalValue) }
    val scaleFactor = portfolioTotal.divide(holdingsTotal, 10, RoundingMode.HALF_UP)

    return holdingsMap
      .map { (key, value) ->
        val scaledValue = value.totalValue.multiply(scaleFactor)
        val percentage = scaledValue.multiply(BigDecimal(100)).divide(portfolioTotal, 4, RoundingMode.HALF_UP)

        EtfHoldingBreakdownDto(
          holdingTicker = key.ticker,
          holdingName = key.name,
          percentageOfTotal = percentage,
          totalValueEur = scaledValue.setScale(2, RoundingMode.HALF_UP),
          holdingSector = key.sector,
          inEtfs = value.etfSymbols.sorted().joinToString(", "),
          numEtfs = value.etfSymbols.size,
        )
      }.filter { it.totalValueEur > BigDecimal.ZERO }
      .sortedByDescending { it.totalValueEur }
  }
}
