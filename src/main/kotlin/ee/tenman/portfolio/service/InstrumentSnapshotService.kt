package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.PriceChangePeriod
import ee.tenman.portfolio.domain.TransactionType
import ee.tenman.portfolio.dto.InstrumentEnrichmentContext
import ee.tenman.portfolio.model.InstrumentSnapshot
import ee.tenman.portfolio.model.PriceChange
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class InstrumentSnapshotService(
  private val instrumentRepository: InstrumentRepository,
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val investmentMetricsService: InvestmentMetricsService,
  private val dailyPriceService: DailyPriceService,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(readOnly = true)
  fun getAllSnapshots(): List<InstrumentSnapshot> = getAllSnapshots(null, null)

  @Transactional(readOnly = true)
  fun getAllSnapshots(platforms: List<String>?): List<InstrumentSnapshot> = getAllSnapshots(platforms, null)

  @Transactional(readOnly = true)
  fun getAllSnapshots(
    platforms: List<String>?,
    period: String?,
  ): List<InstrumentSnapshot> {
    val instruments = instrumentRepository.findAll()
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
    if (filteredTransactions.isEmpty()) {
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

  private fun createInstrumentSnapshot(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    context: InstrumentEnrichmentContext,
  ): InstrumentSnapshot? {
    val metrics =
      investmentMetricsService.calculateInstrumentMetricsWithProfits(instrument, transactions, context.calculationDate)
    val priceChange = calculatePriceChange(instrument, transactions, context)
    if (metrics.quantity.compareTo(BigDecimal.ZERO) == 0 && metrics.totalInvestment.compareTo(BigDecimal.ZERO) == 0) return null
    return InstrumentSnapshot(
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

  private fun calculatePriceChange(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    context: InstrumentEnrichmentContext,
  ): PriceChange? {
    if (transactions.isEmpty()) return null
    val earliestTransaction = transactions.minBy { it.transactionDate }
    val holdingPeriodDays = ChronoUnit.DAYS.between(earliestTransaction.transactionDate, context.calculationDate)
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
    val buyTransactions = transactions.filter { it.transactionType == TransactionType.BUY }
    val totalQuantity = buyTransactions.sumOf { it.quantity }
    if (buyTransactions.isEmpty() || totalQuantity.compareTo(BigDecimal.ZERO) <= 0) return null
    val totalCost =
      buyTransactions.sumOf { transaction ->
        transaction.price.multiply(transaction.quantity).add(transaction.commission)
      }
    val weightedAveragePurchasePrice = totalCost.divide(totalQuantity, 10, RoundingMode.HALF_UP)
    if (weightedAveragePurchasePrice.compareTo(BigDecimal.ZERO) == 0) return null
    val changeAmount = currentPrice.subtract(weightedAveragePurchasePrice)
    val changePercent =
      changeAmount
        .divide(weightedAveragePurchasePrice, 10, RoundingMode.HALF_UP)
        .multiply(BigDecimal(100))
        .toDouble()
    return PriceChange(changeAmount, changePercent)
  }
}
