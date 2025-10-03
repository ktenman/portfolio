package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
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
    val allTransactions = portfolioTransactionRepository.findAllWithInstruments()
    val transactions = allTransactions.groupBy { it.instrument.id }
    val calculationDate = java.time.LocalDate.now(clock)

    val targetPlatforms =
      platforms
        ?.mapNotNull { platformStr ->
      try {
        Platform.valueOf(platformStr.uppercase())
      } catch (e: IllegalArgumentException) {
        null
      }
    }?.toSet()

    return instruments.mapNotNull { instrument ->
      instrument.apply {
        val allInstrumentTransactions = transactions[id] ?: emptyList()

        val filteredTransactions =
          if (targetPlatforms != null) {
          allInstrumentTransactions.filter { transaction ->
            targetPlatforms.contains(transaction.platform)
          }
        } else {
          allInstrumentTransactions
        }

        if (filteredTransactions.isEmpty()) {
          if (targetPlatforms == null) {
            return@mapNotNull instrument
          }
          return@mapNotNull null
        }

        val metrics = investmentMetricsService.calculateInstrumentMetricsWithProfits(this, filteredTransactions, calculationDate)
        totalInvestment = metrics.totalInvestment
        currentValue = metrics.currentValue
        profit = metrics.profit
        xirr = metrics.xirr
        quantity = metrics.quantity
        this.platforms = filteredTransactions.map { it.platform }.toSet()

        if (quantity == BigDecimal.ZERO && totalInvestment == BigDecimal.ZERO) {
          if (targetPlatforms == null) {
            return@mapNotNull instrument
          }
          return@mapNotNull null
        }
      }
    }
  }

  fun findInstrument(id: Long): Instrument = getInstrumentById(id)

  @Cacheable(value = [INSTRUMENT_CACHE], key = "#symbol", unless = "#result == null")
  fun getInstrument(symbol: String): Instrument =
    instrumentRepository
      .findBySymbol(symbol)
      .orElseThrow { RuntimeException("Instrument not found with symbol: $symbol") }
}
