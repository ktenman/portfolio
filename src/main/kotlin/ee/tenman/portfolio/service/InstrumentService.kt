package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.PriceChangePeriod
import ee.tenman.portfolio.dto.InstrumentEnrichmentContext
import ee.tenman.portfolio.exception.EntityNotFoundException
import ee.tenman.portfolio.model.InstrumentSnapshot
import ee.tenman.portfolio.model.PriceChange
import ee.tenman.portfolio.model.TransactionState
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Service
class InstrumentService(
  private val instrumentRepository: InstrumentRepository,
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val investmentMetricsService: InvestmentMetricsService,
  private val dailyPriceService: DailyPriceService,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(readOnly = true)
  @Cacheable(value = [INSTRUMENT_CACHE], key = "#id")
  fun getInstrumentById(id: Long): Instrument =
    instrumentRepository
      .findById(
        id,
      ).orElseThrow {
        ee.tenman.portfolio.exception
          .EntityNotFoundException("Instrument not found with id: $id")
      }

  @Transactional(readOnly = true)
  fun findBySymbol(symbol: String): Instrument? =
    instrumentRepository.findBySymbol(symbol).orElseThrow {
    EntityNotFoundException("Instrument not found with symbol: $symbol")
  }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(
        value = [INSTRUMENT_CACHE],
        key = "#instrument.id",
        condition = "#instrument.id != null",
      ),
      CacheEvict(value = [INSTRUMENT_CACHE], key = "#instrument.symbol"),
      CacheEvict(
        value = [INSTRUMENT_CACHE],
        key = "'allInstruments'",
      ),
      CacheEvict(value = [SUMMARY_CACHE], allEntries = true),
      CacheEvict(
        value = [TRANSACTION_CACHE],
        allEntries = true,
      ),
      CacheEvict(value = [ONE_DAY_CACHE], allEntries = true),
    ],
  )
  fun saveInstrument(instrument: Instrument): Instrument {
    val saved = instrumentRepository.save(instrument)
    recalculateTransactionProfitsForInstrument(saved.id)
    return saved
  }

  private fun recalculateTransactionProfitsForInstrument(instrumentId: Long) {
    val updatedInstrument = instrumentRepository.findById(instrumentId).orElse(null) ?: return
    val transactions = portfolioTransactionRepository.findAllByInstrumentId(instrumentId)

    if (transactions.isNotEmpty()) {
      transactions.forEach { it.instrument = updatedInstrument }

      transactions
        .groupBy { it.platform }
        .forEach { (_, platformTransactions) ->
          calculateProfitsForPlatform(platformTransactions.sortedWith(compareBy({ it.transactionDate }, { it.id })))
        }

      portfolioTransactionRepository.saveAll(transactions)
    }
  }

  private fun calculateProfitsForPlatform(transactions: List<PortfolioTransaction>) {
    val sortedTransactions = transactions.sortedWith(compareBy({ it.transactionDate }, { it.id }))
    val (totalCost, currentQuantity) = processTransactions(sortedTransactions)
    val currentPrice = sortedTransactions.firstOrNull()?.instrument?.currentPrice ?: BigDecimal.ZERO
    val averageCost = calculateAverageCost(totalCost, currentQuantity)
    val totalUnrealizedProfit = calculateUnrealizedProfit(currentQuantity, currentPrice, averageCost)
    val buyTransactions = sortedTransactions.filter { it.transactionType == ee.tenman.portfolio.domain.TransactionType.BUY }
    distributeProfitsToBuyTransactions(buyTransactions, currentQuantity, averageCost, totalUnrealizedProfit)
  }

  private fun processTransactions(transactions: List<PortfolioTransaction>): TransactionState =
    transactions.fold(TransactionState(BigDecimal.ZERO, BigDecimal.ZERO)) { state, transaction ->
      when (transaction.transactionType) {
        ee.tenman.portfolio.domain.TransactionType.BUY -> processBuyTransaction(transaction, state)
        ee.tenman.portfolio.domain.TransactionType.SELL -> processSellTransaction(transaction, state)
      }
    }

  private fun processBuyTransaction(
    transaction: PortfolioTransaction,
    state: TransactionState,
  ): TransactionState {
    val cost = transaction.price.multiply(transaction.quantity).add(transaction.commission)
    transaction.realizedProfit = BigDecimal.ZERO
    return TransactionState(state.totalCost.add(cost), state.currentQuantity.add(transaction.quantity))
  }

  private fun processSellTransaction(
    transaction: PortfolioTransaction,
    state: TransactionState,
  ): TransactionState {
    val averageCost = calculateAverageCost(state.totalCost, state.currentQuantity)
    transaction.averageCost = averageCost
    transaction.realizedProfit = transaction.quantity.multiply(transaction.price.subtract(averageCost)).subtract(transaction.commission)
    transaction.unrealizedProfit = BigDecimal.ZERO
    transaction.remainingQuantity = BigDecimal.ZERO
    if (state.currentQuantity <= BigDecimal.ZERO) return state
    val sellRatio = transaction.quantity.divide(state.currentQuantity, 10, java.math.RoundingMode.HALF_UP)
    return TransactionState(
      state.totalCost.multiply(BigDecimal.ONE.subtract(sellRatio)),
      state.currentQuantity.subtract(transaction.quantity),
    )
  }

  private fun calculateAverageCost(
    totalCost: BigDecimal,
    quantity: BigDecimal,
  ): BigDecimal = if (quantity > BigDecimal.ZERO) totalCost.divide(quantity, 10, java.math.RoundingMode.HALF_UP) else BigDecimal.ZERO

  private fun calculateUnrealizedProfit(
    quantity: BigDecimal,
    price: BigDecimal,
    avgCost: BigDecimal,
  ): BigDecimal = if (quantity > BigDecimal.ZERO && price > BigDecimal.ZERO) quantity.multiply(price.subtract(avgCost)) else BigDecimal.ZERO

  private fun distributeProfitsToBuyTransactions(
    buyTransactions: List<PortfolioTransaction>,
    currentQuantity: BigDecimal,
    averageCost: BigDecimal,
    totalUnrealizedProfit: BigDecimal,
  ) {
    if (currentQuantity <= BigDecimal.ZERO) {
      buyTransactions.forEach {
        it.remainingQuantity = BigDecimal.ZERO
        it.unrealizedProfit = BigDecimal.ZERO
        it.averageCost = it.price
      }
      return
    }
    val totalBuyQuantity = buyTransactions.sumOf { it.quantity }
    buyTransactions.forEach { buyTx ->
      val proportionalQuantity =
        buyTx.quantity
        .multiply(currentQuantity)
        .divide(totalBuyQuantity, 10, java.math.RoundingMode.HALF_UP)
      buyTx.remainingQuantity = proportionalQuantity
      buyTx.averageCost = averageCost
      buyTx.unrealizedProfit =
        totalUnrealizedProfit
        .multiply(proportionalQuantity)
        .divide(currentQuantity, 10, java.math.RoundingMode.HALF_UP)
    }
  }

  @Transactional
  @Caching(
    evict = [
      CacheEvict(value = [INSTRUMENT_CACHE], key = "#id"),
      CacheEvict(
        value = [INSTRUMENT_CACHE],
        key = "'allInstruments'",
      ),
    ],
  )
  fun deleteInstrument(id: Long) = instrumentRepository.deleteById(id)

  @Transactional(readOnly = true)
  fun getAllInstrumentsWithoutFiltering(): List<Instrument> = instrumentRepository.findAll()

  @Transactional(readOnly = true)
  fun getAllInstrumentSnapshots(): List<InstrumentSnapshot> = getAllInstrumentSnapshots(null, null)

  @Transactional(readOnly = true)
  fun getAllInstrumentSnapshots(platforms: List<String>?): List<InstrumentSnapshot> = getAllInstrumentSnapshots(platforms, null)

  @Transactional(readOnly = true)
  fun getAllInstrumentSnapshots(
    platforms: List<String>?,
    period: String?,
  ): List<InstrumentSnapshot> {
    val instruments = getAllInstrumentsWithoutFiltering().toList()
    val transactionsByInstrument = portfolioTransactionRepository.findAllWithInstruments().groupBy { it.instrument.id }
    val context =
      InstrumentEnrichmentContext(
        calculationDate = LocalDate.now(clock),
        priceChangePeriod = period?.let { PriceChangePeriod.fromString(it) } ?: PriceChangePeriod.P24H,
        targetPlatforms = parsePlatformFilters(platforms),
      )

    return instruments.mapNotNull { instrument ->
      enrichInstrumentWithMetrics(instrument, transactionsByInstrument, context)
    }
  }

  private fun parsePlatformFilters(platforms: List<String>?): Set<Platform>? =
    platforms
      ?.mapNotNull { platformStr ->
      runCatching { Platform.valueOf(platformStr.uppercase()) }
        .onFailure { log.debug("Invalid platform filter: {}", platformStr, it) }
        .getOrNull()
    }?.toSet()

  private fun enrichInstrumentWithMetrics(
    instrument: Instrument,
    transactionsByInstrument: Map<Long, List<PortfolioTransaction>>,
    context: InstrumentEnrichmentContext,
  ): InstrumentSnapshot? {
    val allTransactions = transactionsByInstrument[instrument.id] ?: emptyList()
    val filteredTransactions = filterTransactionsByPlatforms(allTransactions, context.targetPlatforms)

    if (!shouldIncludeInstrument(filteredTransactions)) {
      return if (context.targetPlatforms == null) InstrumentSnapshot(instrument) else null
    }

    return createInstrumentSnapshot(instrument, filteredTransactions, context)
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

  private fun shouldIncludeInstrument(filteredTransactions: List<PortfolioTransaction>): Boolean = filteredTransactions.isNotEmpty()

  private fun createInstrumentSnapshot(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    context: InstrumentEnrichmentContext,
  ): InstrumentSnapshot? {
    val metrics =
      investmentMetricsService.calculateInstrumentMetricsWithProfits(instrument, transactions, context.calculationDate)

    val priceChange = calculatePriceChange(instrument, transactions, context)

    return if (metrics.quantity == BigDecimal.ZERO && metrics.totalInvestment == BigDecimal.ZERO) {
      null
    } else {
      InstrumentSnapshot(
        instrument = instrument,
        totalInvestment = metrics.totalInvestment,
        currentValue = metrics.currentValue,
        profit = metrics.profit,
        realizedProfit = metrics.realizedProfit,
        unrealizedProfit = metrics.unrealizedProfit ?: BigDecimal.ZERO,
        xirr = metrics.xirr,
        quantity = metrics.quantity,
        platforms = transactions.map { it.platform }.toSet(),
        priceChangeAmount = priceChange?.changeAmount?.multiply(metrics.quantity),
        priceChangePercent = priceChange?.changePercent,
      )
    }
  }

  private fun calculatePriceChange(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    context: InstrumentEnrichmentContext,
  ): PriceChange? {
    if (transactions.isEmpty()) return null

    val earliestTransaction = transactions.minByOrNull { it.transactionDate } ?: return null
    val holdingPeriodDays =
      java.time.temporal.ChronoUnit.DAYS
        .between(earliestTransaction.transactionDate, context.calculationDate)

    return if (holdingPeriodDays >= context.priceChangePeriod.days) {
      dailyPriceService.getPriceChange(instrument, context.priceChangePeriod)
    } else {
      calculatePriceChangeSincePurchase(instrument, transactions)
    }
  }

  private fun calculatePriceChangeSincePurchase(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
  ): PriceChange? {
    val currentPrice = instrument.currentPrice ?: return null

    val buyTransactions = transactions.filter { it.transactionType == ee.tenman.portfolio.domain.TransactionType.BUY }
    if (buyTransactions.isEmpty()) return null

    val totalQuantity = buyTransactions.sumOf { it.quantity }
    if (totalQuantity <= BigDecimal.ZERO) return null

    val totalCost =
      buyTransactions.sumOf { transaction ->
        transaction.price.multiply(transaction.quantity).add(transaction.commission)
      }

    val weightedAveragePurchasePrice =
      totalCost.divide(totalQuantity, 10, java.math.RoundingMode.HALF_UP)

    val changeAmount = currentPrice.subtract(weightedAveragePurchasePrice)
    val changePercent =
      changeAmount
        .divide(weightedAveragePurchasePrice, 10, java.math.RoundingMode.HALF_UP)
        .multiply(BigDecimal(100))
        .toDouble()

    return PriceChange(changeAmount, changePercent)
  }
}
