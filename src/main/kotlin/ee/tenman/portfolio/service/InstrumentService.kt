package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class InstrumentService(
  private val instrumentRepository: InstrumentRepository,
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val investmentMetricsService: InvestmentMetricsService,
  private val dailyPriceService: DailyPriceService,
  private val clock: java.time.Clock,
) {
  @Transactional(readOnly = true)
  @Cacheable(value = [INSTRUMENT_CACHE], key = "#id")
  fun getInstrumentById(id: Long): Instrument =
    instrumentRepository.findById(id).orElseThrow { RuntimeException("Instrument not found with id: $id") }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(
        value = [INSTRUMENT_CACHE],
        key = "#instrument.id",
        condition = "#instrument.id != null",
      ), CacheEvict(value = [INSTRUMENT_CACHE], key = "#instrument.symbol"), CacheEvict(
        value = [INSTRUMENT_CACHE],
        key = "'allInstruments'",
      ),
    ],
  )
  fun saveInstrument(instrument: Instrument): Instrument = instrumentRepository.save(instrument)

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [INSTRUMENT_CACHE], key = "#id"), CacheEvict(
        value = [INSTRUMENT_CACHE],
        key = "'allInstruments'",
      ),
    ],
  )
  fun deleteInstrument(id: Long) = instrumentRepository.deleteById(id)

  @Transactional(readOnly = true)
  fun getAllInstruments(): List<Instrument> = getAllInstruments(null)

  @Transactional(readOnly = true)
  fun getAllInstruments(platforms: List<String>?): List<Instrument> {
    val instruments = instrumentRepository.findAll()
    val transactionsByInstrument = portfolioTransactionRepository.findAllWithInstruments().groupBy { it.instrument.id }
    val calculationDate = java.time.LocalDate.now(clock)
    val targetPlatforms = parsePlatformFilters(platforms)

    return instruments.mapNotNull { instrument ->
      enrichInstrumentWithMetrics(instrument, transactionsByInstrument, targetPlatforms, calculationDate)
    }
  }

  private fun parsePlatformFilters(platforms: List<String>?): Set<Platform>? =
    platforms
      ?.mapNotNull { platformStr ->
      try {
        Platform.valueOf(platformStr.uppercase())
      } catch (e: IllegalArgumentException) {
        null
      }
    }?.toSet()

  private fun enrichInstrumentWithMetrics(
    instrument: Instrument,
    transactionsByInstrument: Map<Long, List<PortfolioTransaction>>,
    targetPlatforms: Set<Platform>?,
    calculationDate: java.time.LocalDate,
  ): Instrument? {
    val allTransactions = transactionsByInstrument[instrument.id] ?: emptyList()
    val filteredTransactions = filterTransactionsByPlatforms(allTransactions, targetPlatforms)

    if (!shouldIncludeInstrument(filteredTransactions, targetPlatforms)) {
      return if (targetPlatforms == null) instrument else null
    }

    return applyInstrumentMetrics(instrument, filteredTransactions, calculationDate)
  }

  private fun filterTransactionsByPlatforms(
    transactions: List<PortfolioTransaction>,
    targetPlatforms: Set<Platform>?,
  ): List<PortfolioTransaction> =
    if (targetPlatforms != null) {
      transactions.filter { targetPlatforms.contains(it.platform) }
    } else {
      transactions
    }

  private fun shouldIncludeInstrument(
    filteredTransactions: List<PortfolioTransaction>,
    targetPlatforms: Set<Platform>?,
  ): Boolean = filteredTransactions.isNotEmpty()

  private fun applyInstrumentMetrics(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    calculationDate: java.time.LocalDate,
  ): Instrument? {
    val metrics = investmentMetricsService.calculateInstrumentMetricsWithProfits(instrument, transactions, calculationDate)

    instrument.totalInvestment = metrics.totalInvestment
    instrument.currentValue = metrics.currentValue
    instrument.profit = metrics.profit
    instrument.xirr = metrics.xirr
    instrument.quantity = metrics.quantity
    instrument.platforms = transactions.map { it.platform }.toSet()

    val priceChange = dailyPriceService.getLastPriceChange(instrument)
    instrument.priceChangeAmount = priceChange?.changeAmount?.multiply(metrics.quantity)
    instrument.priceChangePercent = priceChange?.changePercent

    return if (metrics.quantity == BigDecimal.ZERO && metrics.totalInvestment == BigDecimal.ZERO) {
      null
    } else {
      instrument
    }
  }

  fun findInstrument(id: Long): Instrument = getInstrumentById(id)

  @Cacheable(value = [INSTRUMENT_CACHE], key = "#symbol", unless = "#result == null")
  fun getInstrument(symbol: String): Instrument =
    instrumentRepository
      .findBySymbol(symbol)
      .orElseThrow { RuntimeException("Instrument not found with symbol: $symbol") }
}
