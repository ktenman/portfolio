package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.InstrumentRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InstrumentService(
  private val instrumentRepository: InstrumentRepository,
  private val portfolioTransactionService: PortfolioTransactionService,
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
  fun getAllInstruments(): List<Instrument> {
    val instruments = instrumentRepository.findAll()
    val transactions = portfolioTransactionService.getAllTransactions().groupBy { it.instrument.id }
    val calculationDate = java.time.LocalDate.now(clock)

    return instruments.map { instrument ->
      instrument.apply {
        val metrics = investmentMetricsService.calculateInstrumentMetrics(this, transactions[id] ?: emptyList(), calculationDate)
        totalInvestment = metrics.totalInvestment
        currentValue = metrics.currentValue
        profit = metrics.profit
        xirr = metrics.xirr
        quantity = metrics.quantity
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
