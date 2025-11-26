package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.PriceChangePeriod
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.exception.EntityNotFoundException
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

private const val CALCULATION_SCALE = 10

data class InstrumentEnrichmentContext(
  val date: LocalDate,
  val period: PriceChangePeriod,
  val platforms: Set<Platform>?,
)

data class ProfitState(
  val quantity: BigDecimal = BigDecimal.ZERO,
  val cost: BigDecimal = BigDecimal.ZERO,
)

@Service
class InstrumentService(
  private val instrumentRepository: InstrumentRepository,
  private val transactionRepository: PortfolioTransactionRepository,
  private val investmentMetricsService: InvestmentMetricsService,
  private val dailyPriceService: DailyPriceService,
  private val clock: Clock,
) {
  @Transactional(readOnly = true)
  @Cacheable(value = [INSTRUMENT_CACHE], key = "#id")
  fun getInstrument(id: Long): Instrument =
    instrumentRepository.findById(id).orElseThrow { EntityNotFoundException("Instrument not found with id: $id") }

  @Transactional(readOnly = true)
  fun findBySymbol(symbol: String): Instrument? =
    instrumentRepository.findBySymbol(symbol).orElseThrow { EntityNotFoundException("Instrument not found with symbol: $symbol") }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [INSTRUMENT_CACHE], key = "#instrument.id", condition = "#instrument.id != null"),
      CacheEvict(value = [INSTRUMENT_CACHE], key = "#instrument.symbol"),
      CacheEvict(value = [INSTRUMENT_CACHE], key = "'allInstruments'"),
      CacheEvict(value = [SUMMARY_CACHE], allEntries = true),
      CacheEvict(value = [TRANSACTION_CACHE], allEntries = true),
      CacheEvict(value = [ONE_DAY_CACHE], allEntries = true),
    ],
  )
  fun save(instrument: Instrument): Instrument {
    val saved = instrumentRepository.save(instrument)
    recalculate(saved.id)
    return saved
  }

  private fun recalculate(id: Long) {
    val instrument = instrumentRepository.findById(id).orElse(null) ?: return
    val transactions = transactionRepository.findAllByInstrumentId(id)
    if (transactions.isEmpty()) return
    transactions.forEach { it.instrument = instrument }
    transactions.groupBy { it.platform }.values.forEach { calculateProfits(it.sortedWith(compareBy({ it.transactionDate }, { it.id }))) }
    transactionRepository.saveAll(transactions)
  }

  private fun calculateProfits(transactions: List<PortfolioTransaction>) {
    val sorted = transactions.sortedWith(compareBy({ it.transactionDate }, { it.id }))
    val state = sorted.fold(ProfitState()) { acc, tx -> process(tx, acc) }
    val price = sorted.firstOrNull()?.instrument?.currentPrice ?: BigDecimal.ZERO
    val average = divide(state.cost, state.quantity)
    val unrealized = multiply(state.quantity, price.subtract(average))
    distribute(sorted.filter { it.transactionType == TransactionType.BUY }, state.quantity, average, unrealized)
  }

  private fun process(tx: PortfolioTransaction, state: ProfitState): ProfitState =
    when (tx.transactionType) {
      TransactionType.BUY -> buy(tx, state)
      TransactionType.SELL -> sell(tx, state)
    }

  private fun buy(tx: PortfolioTransaction, state: ProfitState): ProfitState {
    val cost = tx.price.multiply(tx.quantity).add(tx.commission)
    tx.realizedProfit = BigDecimal.ZERO
    return ProfitState(state.quantity.add(tx.quantity), state.cost.add(cost))
  }

  private fun sell(tx: PortfolioTransaction, state: ProfitState): ProfitState {
    val average = divide(state.cost, state.quantity)
    tx.averageCost = average
    tx.realizedProfit = tx.quantity.multiply(tx.price.subtract(average)).subtract(tx.commission)
    tx.unrealizedProfit = BigDecimal.ZERO
    tx.remainingQuantity = BigDecimal.ZERO
    if (state.quantity <= BigDecimal.ZERO) return state
    val ratio = tx.quantity.divide(state.quantity, CALCULATION_SCALE, RoundingMode.HALF_UP)
    return ProfitState(state.quantity.subtract(tx.quantity), state.cost.multiply(BigDecimal.ONE.subtract(ratio)))
  }

  private fun distribute(buys: List<PortfolioTransaction>, quantity: BigDecimal, average: BigDecimal, unrealized: BigDecimal) {
    if (quantity <= BigDecimal.ZERO) {
      buys.forEach { it.remainingQuantity = BigDecimal.ZERO; it.unrealizedProfit = BigDecimal.ZERO; it.averageCost = it.price }
      return
    }
    val total = buys.sumOf { it.quantity }
    buys.forEach { tx ->
      val proportional = tx.quantity.multiply(quantity).divide(total, CALCULATION_SCALE, RoundingMode.HALF_UP)
      tx.remainingQuantity = proportional
      tx.averageCost = average
      tx.unrealizedProfit = unrealized.multiply(proportional).divide(quantity, CALCULATION_SCALE, RoundingMode.HALF_UP)
    }
  }

  private fun divide(numerator: BigDecimal, denominator: BigDecimal): BigDecimal =
    if (denominator > BigDecimal.ZERO) numerator.divide(denominator, CALCULATION_SCALE, RoundingMode.HALF_UP) else BigDecimal.ZERO

  private fun multiply(a: BigDecimal, b: BigDecimal): BigDecimal =
    if (a > BigDecimal.ZERO && b != BigDecimal.ZERO) a.multiply(b) else BigDecimal.ZERO

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [INSTRUMENT_CACHE], key = "#id"),
      CacheEvict(value = [INSTRUMENT_CACHE], key = "'allInstruments'"),
    ],
  )
  fun deleteInstrument(id: Long) = instrumentRepository.deleteById(id)

  @Transactional(readOnly = true)
  fun findAll(): List<Instrument> = instrumentRepository.findAll()

  @Transactional(readOnly = true)
  fun getAllInstruments(): List<Instrument> = getAllInstruments(null, null)

  @Transactional(readOnly = true)
  fun getAllInstruments(platforms: List<String>?): List<Instrument> = getAllInstruments(platforms, null)

  @Transactional(readOnly = true)
  fun getAllInstruments(platforms: List<String>?, period: String?): List<Instrument> {
    val all = findAll()
    val grouped = transactionRepository.findAllWithInstruments().groupBy { it.instrument.id }
    val context = InstrumentEnrichmentContext(
      date = LocalDate.now(clock),
      period = period?.let { PriceChangePeriod.fromString(it) } ?: PriceChangePeriod.P24H,
      platforms = parse(platforms),
    )
    return all.mapNotNull { enrich(it, grouped, context) }
  }

  private fun parse(platforms: List<String>?): Set<Platform>? =
    platforms?.mapNotNull { runCatching { Platform.valueOf(it.uppercase()) }.getOrNull() }?.toSet()

  private fun enrich(instrument: Instrument, grouped: Map<Long, List<PortfolioTransaction>>, context: InstrumentEnrichmentContext): Instrument? {
    val all = grouped[instrument.id] ?: emptyList()
    val filtered = context.platforms?.let { p -> all.filter { p.contains(it.platform) } } ?: all
    if (filtered.isEmpty()) return if (context.platforms == null) instrument else null
    return apply(instrument, filtered, context)
  }

  private fun apply(instrument: Instrument, transactions: List<PortfolioTransaction>, context: InstrumentEnrichmentContext): Instrument? {
    val result = investmentMetricsService.calculateInstrumentMetricsWithProfits(instrument, transactions, context.date)
    instrument.totalInvestment = result.totalInvestment
    instrument.currentValue = result.currentValue
    instrument.profit = result.profit
    instrument.realizedProfit = result.realizedProfit
    instrument.unrealizedProfit = result.unrealizedProfit ?: BigDecimal.ZERO
    instrument.xirr = result.xirr
    instrument.quantity = result.quantity
    instrument.platforms = transactions.map { it.platform }.toSet()
    val change = change(instrument, transactions, context)
    instrument.priceChangeAmount = change?.changeAmount?.multiply(result.quantity)
    instrument.priceChangePercent = change?.changePercent
    return if (result.quantity == BigDecimal.ZERO && result.totalInvestment == BigDecimal.ZERO) null else instrument
  }

  private fun change(instrument: Instrument, transactions: List<PortfolioTransaction>, context: InstrumentEnrichmentContext): PriceChange? {
    if (transactions.isEmpty()) return null
    val earliest = transactions.minByOrNull { it.transactionDate } ?: return null
    val days = java.time.temporal.ChronoUnit.DAYS.between(earliest.transactionDate, context.date)
    return if (days >= context.period.days) dailyPriceService.getPriceChange(instrument, context.period) else sincePurchase(instrument, transactions)
  }

  private fun sincePurchase(instrument: Instrument, transactions: List<PortfolioTransaction>): PriceChange? {
    val price = instrument.currentPrice ?: return null
    val buys = transactions.filter { it.transactionType == TransactionType.BUY }
    if (buys.isEmpty()) return null
    val quantity = buys.sumOf { it.quantity }
    if (quantity <= BigDecimal.ZERO) return null
    val cost = buys.sumOf { it.price.multiply(it.quantity).add(it.commission) }
    val average = cost.divide(quantity, CALCULATION_SCALE, RoundingMode.HALF_UP)
    val amount = price.subtract(average)
    val percent = amount.divide(average, CALCULATION_SCALE, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toDouble()
    return PriceChange(amount, percent)
  }
}
