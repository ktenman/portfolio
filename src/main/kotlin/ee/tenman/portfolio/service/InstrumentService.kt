package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.INSTRUMENT_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.ONE_DAY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.SUMMARY_CACHE
import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.TRANSACTION_CACHE
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.PriceChangePeriod
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

data class InstrumentEnrichmentContext(
  val calculationDate: LocalDate,
  val priceChangePeriod: PriceChangePeriod,
  val targetPlatforms: Set<Platform>?,
)

@Service
class InstrumentService(
  private val instrumentRepository: InstrumentRepository,
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val investmentMetricsService: InvestmentMetricsService,
  private val dailyPriceService: DailyPriceService,
  private val clock: Clock,
) {
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
          calculateProfitsForPlatform(platformTransactions.sortedBy { it.transactionDate })
        }

      portfolioTransactionRepository.saveAll(transactions)
    }
  }

  private fun calculateProfitsForPlatform(transactions: List<PortfolioTransaction>) {
    val sortedTransactions = transactions.sortedBy { it.transactionDate }
    var currentQuantity = BigDecimal.ZERO
    var totalCost = BigDecimal.ZERO

    sortedTransactions.forEach { transaction ->
      when (transaction.transactionType) {
        ee.tenman.portfolio.domain.TransactionType.BUY -> {
          val cost = transaction.price.multiply(transaction.quantity).add(transaction.commission)
          transaction.realizedProfit = BigDecimal.ZERO
          totalCost = totalCost.add(cost)
          currentQuantity = currentQuantity.add(transaction.quantity)
        }

        ee.tenman.portfolio.domain.TransactionType.SELL -> {
          val averageCost =
            if (currentQuantity > BigDecimal.ZERO) {
              totalCost.divide(currentQuantity, 10, java.math.RoundingMode.HALF_UP)
            } else {
              BigDecimal.ZERO
            }
          transaction.averageCost = averageCost

          val grossProfit =
            transaction.quantity.multiply(transaction.price.subtract(averageCost))

          transaction.realizedProfit = grossProfit.subtract(transaction.commission)
          transaction.unrealizedProfit = BigDecimal.ZERO
          transaction.remainingQuantity = BigDecimal.ZERO

          if (currentQuantity > BigDecimal.ZERO) {
            val sellRatio = transaction.quantity.divide(currentQuantity, 10, java.math.RoundingMode.HALF_UP)
            totalCost = totalCost.multiply(BigDecimal.ONE.subtract(sellRatio))
            currentQuantity = currentQuantity.subtract(transaction.quantity)
          }
        }
      }
    }

    val currentPrice = sortedTransactions.firstOrNull()?.instrument?.currentPrice ?: BigDecimal.ZERO
    val averageCost =
      if (currentQuantity > BigDecimal.ZERO) {
        totalCost.divide(currentQuantity, 10, java.math.RoundingMode.HALF_UP)
      } else {
        BigDecimal.ZERO
      }
    val totalUnrealizedProfit =
      if (currentQuantity > BigDecimal.ZERO && currentPrice > BigDecimal.ZERO) {
        currentQuantity.multiply(currentPrice.subtract(averageCost))
      } else {
        BigDecimal.ZERO
      }

    val buyTransactions =
      sortedTransactions.filter { it.transactionType == ee.tenman.portfolio.domain.TransactionType.BUY }

    if (currentQuantity <= BigDecimal.ZERO) {
      buyTransactions.forEach {
        it.remainingQuantity = BigDecimal.ZERO
        it.unrealizedProfit = BigDecimal.ZERO
        it.averageCost = it.price
      }
    } else {
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
  @Cacheable(value = [INSTRUMENT_CACHE], key = "'allInstruments'")
  fun getAllInstruments(): List<Instrument> = getAllInstruments(null, null)

  @Transactional(readOnly = true)
  fun getAllInstruments(platforms: List<String>?): List<Instrument> = getAllInstruments(platforms, null)

  @Transactional(readOnly = true)
  fun getAllInstruments(
    platforms: List<String>?,
    period: String?,
  ): List<Instrument> {
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
        try {
          Platform.valueOf(platformStr.uppercase())
        } catch (e: IllegalArgumentException) {
          null
        }
      }?.toSet()

  private fun enrichInstrumentWithMetrics(
    instrument: Instrument,
    transactionsByInstrument: Map<Long, List<PortfolioTransaction>>,
    context: InstrumentEnrichmentContext,
  ): Instrument? {
    val allTransactions = transactionsByInstrument[instrument.id] ?: emptyList()
    val filteredTransactions = filterTransactionsByPlatforms(allTransactions, context.targetPlatforms)

    if (!shouldIncludeInstrument(filteredTransactions)) {
      return if (context.targetPlatforms == null) instrument else null
    }

    return applyInstrumentMetrics(instrument, filteredTransactions, context)
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

  private fun applyInstrumentMetrics(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    context: InstrumentEnrichmentContext,
  ): Instrument? {
    val metrics =
      investmentMetricsService.calculateInstrumentMetricsWithProfits(instrument, transactions, context.calculationDate)

    instrument.totalInvestment = metrics.totalInvestment
    instrument.currentValue = metrics.currentValue
    instrument.profit = metrics.profit
    instrument.realizedProfit = metrics.realizedProfit
    instrument.unrealizedProfit = metrics.unrealizedProfit ?: BigDecimal.ZERO
    instrument.xirr = metrics.xirr
    instrument.quantity = metrics.quantity
    instrument.platforms = transactions.map { it.platform }.toSet()

    val priceChange = calculatePriceChange(instrument, transactions, context)
    instrument.priceChangeAmount = priceChange?.changeAmount?.multiply(metrics.quantity)
    instrument.priceChangePercent = priceChange?.changePercent

    return if (metrics.quantity == BigDecimal.ZERO && metrics.totalInvestment == BigDecimal.ZERO) {
      null
    } else {
      instrument
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
      calculatePriceChangeSincePurchase(instrument, transactions, context.calculationDate)
    }
  }

  private fun calculatePriceChangeSincePurchase(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    calculationDate: LocalDate,
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
