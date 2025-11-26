package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.domain.TransactionType
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
import java.time.LocalDate

private const val SCALE = 10
private const val DISPLAY_SCALE = 2
private const val PERCENT_SCALE = 4
private val HUNDRED = BigDecimal(100)

@Service
class EtfBreakdownService(
  private val instrumentRepository: InstrumentRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val transactionRepository: PortfolioTransactionRepository,
  private val dailyPriceService: DailyPriceService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  data class HoldingKey(val ticker: String?, val name: String, val sector: String?)
  data class HoldingValue(val total: BigDecimal, val etfs: MutableSet<String>)

  @Cacheable("etf:breakdown", key = "#etfSymbols != null && !#etfSymbols.isEmpty() ? new java.util.TreeSet(#etfSymbols).toString() : 'all'", unless = "#result.isEmpty()")
  fun breakdown(symbols: List<String>? = null): List<EtfHoldingBreakdownDto> {
    val etfs = etfs(symbols)
    log.info("Found ${etfs.size} ETFs: ${etfs.map { it.symbol }}")
    if (etfs.isEmpty()) {
      log.warn("No Lightyear ETFs found")
      return emptyList()
    }
    val total = total(etfs)
    log.info("Actual portfolio total value from transactions: $total")
    val holdings = holdings(etfs)
    log.info("Built holdings map with ${holdings.size} unique holdings")
    val result = aggregate(holdings, total)
    log.info("Returning ${result.size} holdings in breakdown")
    return result
  }

  @CacheEvict("etf:breakdown", allEntries = true)
  fun evict() {
    log.info("Evicting ETF breakdown cache")
  }

  private fun etfs(symbols: List<String>? = null): List<Instrument> {
    val lightyear = instrumentRepository.findByProviderName(ProviderName.LIGHTYEAR)
    val ft = instrumentRepository.findByProviderName(ProviderName.FT)
    log.info("Found ${lightyear.size} LIGHTYEAR instruments and ${ft.size} FT instruments")
    var all = lightyear + ft
    if (!symbols.isNullOrEmpty()) {
      all = all.filter { it.symbol in symbols }
      log.info("Filtered to ${all.size} instruments matching symbols: {}", LogSanitizerUtil.sanitize(symbols))
    }
    val withHoldings = all.filter { hasHoldings(it.id) }
    log.info("${withHoldings.size} instruments have ETF holdings data: ${withHoldings.map { it.symbol }}")
    val active = withHoldings.filter { isActive(it.id) }
    log.info("${active.size} instruments have active positions: ${active.map { it.symbol }}")
    return active
  }

  private fun hasHoldings(id: Long): Boolean = etfPositionRepository.findLatestPositionsByEtfId(id).isNotEmpty()

  private fun isActive(id: Long): Boolean {
    val net = quantity(id)
    log.debug("Instrument $id has net quantity: $net")
    return net > BigDecimal.ZERO
  }

  private fun quantity(id: Long): BigDecimal =
    transactionRepository.findAllByInstrumentId(id).fold(BigDecimal.ZERO) { acc, tx ->
      when (tx.transactionType) {
        TransactionType.BUY -> acc.add(tx.quantity)
        TransactionType.SELL -> acc.subtract(tx.quantity)
      }
    }

  private fun holdings(etfs: List<Instrument>): Map<HoldingKey, HoldingValue> {
    val map = mutableMapOf<HoldingKey, HoldingValue>()
    etfs.forEach { etf ->
      val etfPositions = etfPositionRepository.findLatestPositionsByEtfId(etf.id)
      val qty = quantity(etf.id)
      val price = price(etf)
      etfPositions.forEach { position ->
        val value = value(position, qty, price)
        val key = HoldingKey(position.holding.ticker, position.holding.name, position.holding.sector)
        map.merge(key, HoldingValue(value, mutableSetOf(etf.symbol))) { existing, new ->
          HoldingValue(existing.total.add(new.total), existing.etfs.apply { addAll(new.etfs) })
        }
      }
    }
    return map
  }

  private fun price(instrument: Instrument): BigDecimal {
    val current = instrument.currentPrice
    if (current != null && current > BigDecimal.ZERO) return current
    return runCatching { dailyPriceService.getPrice(instrument, LocalDate.now()) }
      .onFailure { log.warn("No price found for ${instrument.symbol}, using zero") }
      .getOrDefault(BigDecimal.ZERO)
  }

  private fun value(position: EtfPosition, quantity: BigDecimal, price: BigDecimal): BigDecimal {
    val etfValue = quantity.multiply(price)
    val weight = position.weightPercentage.divide(HUNDRED, SCALE, RoundingMode.HALF_UP)
    return etfValue.multiply(weight)
  }

  private fun total(etfs: List<Instrument>): BigDecimal =
    etfs.fold(BigDecimal.ZERO) { acc, etf -> acc.add(quantity(etf.id).multiply(price(etf))) }

  private fun aggregate(holdings: Map<HoldingKey, HoldingValue>, total: BigDecimal): List<EtfHoldingBreakdownDto> {
    if (total == BigDecimal.ZERO) {
      log.warn("Portfolio total is zero, cannot calculate percentages")
      return emptyList()
    }
    val sum = holdings.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v.total) }
    val factor = total.divide(sum, SCALE, RoundingMode.HALF_UP)
    return holdings.map { (key, value) ->
      val scaled = value.total.multiply(factor)
      val percent = scaled.multiply(HUNDRED).divide(total, PERCENT_SCALE, RoundingMode.HALF_UP)
      EtfHoldingBreakdownDto(
        holdingTicker = key.ticker,
        holdingName = key.name,
        percentageOfTotal = percent,
        totalValueEur = scaled.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
        holdingSector = key.sector,
        inEtfs = value.etfs.sorted().joinToString(", "),
        numEtfs = value.etfs.size,
      )
    }.filter { it.totalValueEur > BigDecimal.ZERO }.sortedByDescending { it.totalValueEur }
  }
}
