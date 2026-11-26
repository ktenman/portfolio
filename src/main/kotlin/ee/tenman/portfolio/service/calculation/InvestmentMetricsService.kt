package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.holding.AggregatedHoldings
import ee.tenman.portfolio.model.holding.CurrentHoldings
import ee.tenman.portfolio.model.metrics.InstrumentMetrics
import ee.tenman.portfolio.model.metrics.PortfolioMetrics
import ee.tenman.portfolio.model.metrics.PreparedHolding
import ee.tenman.portfolio.service.pricing.DailyPriceService
import ee.tenman.portfolio.service.pricing.PriceLookup
import ee.tenman.portfolio.service.transaction.TransactionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Service
class InvestmentMetricsService(
  private val dailyPriceService: DailyPriceService,
  private val transactionService: TransactionService,
  private val xirrCalculationService: XirrCalculationService,
  private val holdingsCalculationService: HoldingsCalculationService,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  private fun isToday(date: LocalDate): Boolean = date.isEqual(LocalDate.now(clock))

  fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): CurrentHoldings =
    holdingsCalculationService.calculateCurrentHoldings(transactions)

  fun calculateCurrentValue(
    holdings: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal = holdingsCalculationService.calculateCurrentValue(holdings, currentPrice)

  fun calculateProfit(
    holdings: BigDecimal,
    averageCost: BigDecimal,
    currentPrice: BigDecimal,
  ): BigDecimal = holdingsCalculationService.calculateProfit(holdings, averageCost, currentPrice)

  fun calculateInstrumentMetrics(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    calculationDate: LocalDate? = null,
    priceLookup: PriceLookup? = null,
  ): InstrumentMetrics {
    if (transactions.isEmpty()) return InstrumentMetrics.EMPTY
    val effectiveDate = calculationDate ?: LocalDate.now(clock)
    val aggregated = holdingsCalculationService.calculateAggregatedHoldings(transactions)
    val currentPrice = getEffectivePrice(instrument, priceLookup)
    val currentValue = holdingsCalculationService.calculateCurrentValue(aggregated.totalQuantity, currentPrice)
    val realizedProfit = calculateRealizedProfit(transactions)
    val unrealizedProfit = currentValue.subtract(aggregated.totalInvestment)
    val cashFlows = xirrCalculationService.buildCashFlows(transactions, currentValue, effectiveDate)
    return InstrumentMetrics(
      totalInvestment = aggregated.totalInvestment,
      currentValue = currentValue,
      profit = realizedProfit.add(unrealizedProfit),
      realizedProfit = realizedProfit,
      unrealizedProfit = unrealizedProfit,
      xirr = xirrCalculationService.calculateAdjustedXirr(cashFlows, effectiveDate),
      quantity = aggregated.totalQuantity,
    )
  }

  fun calculateInstrumentMetricsWithProfits(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    calculationDate: LocalDate? = null,
    priceLookup: PriceLookup? = null,
  ): InstrumentMetrics {
    if (transactions.isEmpty()) return InstrumentMetrics.EMPTY
    val effectiveDate = calculationDate ?: LocalDate.now(clock)
    val currentPrice = getEffectivePrice(instrument, priceLookup)
    transactionService.calculateTransactionProfits(transactions, currentPrice)
    return calculateInstrumentMetrics(instrument, transactions, effectiveDate, priceLookup)
  }

  private fun getEffectivePrice(
    instrument: Instrument,
    priceLookup: PriceLookup? = null,
  ): BigDecimal =
    livePrice(instrument)
      ?: getEffectivePriceForDate(instrument, LocalDate.now(clock), priceLookup)
      ?: BigDecimal.ZERO

  private fun livePrice(instrument: Instrument): BigDecimal? =
    instrument.cashPriceOrNull()
      ?: instrument.currentPrice?.takeIf { it > BigDecimal.ZERO }

  fun prepareHoldings(instrumentGroups: Map<Instrument, List<PortfolioTransaction>>): List<PreparedHolding> {
    transactionService.calculateTransactionProfits(instrumentGroups.values.flatten())
    return instrumentGroups.map { (instrument, transactions) -> prepareHolding(instrument, transactions) }
  }

  fun prepareHoldingPrefixes(
    instrument: Instrument,
    sortedTransactions: List<PortfolioTransaction>,
  ): List<PreparedHolding> {
    transactionService.calculateTransactionProfits(sortedTransactions)
    val aggregated = holdingsCalculationService.calculateAggregatedHoldingPrefixes(sortedTransactions)
    val realized = sortedTransactions.runningFold(BigDecimal.ZERO) { sum, tx -> sum.add(InvestmentMath.realizedProfit(tx)) }
    val cashFlows = sortedTransactions.map(xirrCalculationService::convertToCashFlow)
    return sortedTransactions.indices.map { index ->
      val count = index + 1
      PreparedHolding(instrument, sortedTransactions.subList(0, count), aggregated[index], realized[count], cashFlows.subList(0, count))
    }
  }

  fun valuePortfolio(
    holdings: List<PreparedHolding>,
    date: LocalDate,
    priceLookup: PriceLookup? = null,
  ): PortfolioMetrics {
    val metrics = PortfolioMetrics()
    holdings.forEach { holding -> valueHolding(holding, date, metrics, priceLookup) }
    return metrics
  }

  private fun prepareHolding(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
  ): PreparedHolding =
    PreparedHolding(
      instrument = instrument,
      transactions = transactions,
      aggregated = aggregate(transactions),
      realizedProfit = calculateRealizedProfit(transactions),
      cashFlows = transactions.map(xirrCalculationService::convertToCashFlow),
    )

  private fun aggregate(transactions: List<PortfolioTransaction>): AggregatedHoldings? =
    runCatching { holdingsCalculationService.calculateAggregatedHoldings(transactions) }
      .onFailure { log.warn("Aggregating holdings failed, using fallback: ${it.message}") }
      .getOrNull()

  private fun valueHolding(
    holding: PreparedHolding,
    date: LocalDate,
    metrics: PortfolioMetrics,
    priceLookup: PriceLookup?,
  ) {
    val aggregated = holding.aggregated ?: return processInstrumentWithFallback(holding, date, metrics, priceLookup)
    runCatching { processInstrumentWithUnifiedCalculation(holding, aggregated, date, metrics, priceLookup) }
      .onFailure {
        log.warn("Unified calculation failed, using fallback: ${it.message}")
        processInstrumentWithFallback(holding, date, metrics, priceLookup)
      }
  }

  private fun processInstrumentWithUnifiedCalculation(
    holding: PreparedHolding,
    aggregated: AggregatedHoldings,
    date: LocalDate,
    metrics: PortfolioMetrics,
    priceLookup: PriceLookup?,
  ) {
    val currentValue = calculateCurrentValueForDate(aggregated.totalQuantity, holding.instrument, date, priceLookup)
    val unrealizedProfit = currentValue.subtract(aggregated.totalInvestment)
    if (hasNothingToAdd(currentValue, holding.realizedProfit)) return
    updateMetrics(metrics, currentValue, holding.realizedProfit, unrealizedProfit)
    xirrCalculationService.addCashFlows(metrics.xirrCashFlows, holding.cashFlows, currentValue, date)
  }

  private fun processInstrumentWithFallback(
    holding: PreparedHolding,
    date: LocalDate,
    metrics: PortfolioMetrics,
    priceLookup: PriceLookup?,
  ) {
    val netQuantity = holdingsCalculationService.calculateNetQuantity(holding.transactions)
    if (netQuantity <= BigDecimal.ZERO) {
      processZeroQuantityFallback(holding, date, metrics)
      return
    }
    val price = getEffectivePriceForDate(holding.instrument, date, priceLookup) ?: return
    val currentValue = netQuantity.multiply(price)
    val (realizedProfit, unrealizedProfit) = calculateFallbackProfits(holding.transactions, currentValue)
    if (hasNothingToAdd(currentValue, realizedProfit)) return
    updateMetrics(metrics, currentValue, realizedProfit, unrealizedProfit)
    xirrCalculationService.addCashFlows(metrics.xirrCashFlows, holding.cashFlows, currentValue, date)
  }

  private fun getEffectivePriceForDate(
    instrument: Instrument,
    date: LocalDate,
    priceLookup: PriceLookup?,
  ): BigDecimal? {
    instrument.cashPriceOrNull()?.let { return it }
    if (priceLookup != null) return priceLookup.priceOnOrBefore(instrument.id, date)
    return runCatching { dailyPriceService.getPrice(instrument, date) }
      .onFailure { log.warn("Skipping ${instrument.symbol} on $date: ${it.message}") }
      .getOrNull()
  }

  private fun processZeroQuantityFallback(
    holding: PreparedHolding,
    date: LocalDate,
    metrics: PortfolioMetrics,
  ) {
    val (realizedProfit, unrealizedProfit) = calculateFallbackProfits(holding.transactions, BigDecimal.ZERO)
    if (hasNothingToAdd(BigDecimal.ZERO, realizedProfit)) return
    updateMetrics(metrics, BigDecimal.ZERO, realizedProfit, unrealizedProfit)
    xirrCalculationService.addCashFlows(metrics.xirrCashFlows, holding.cashFlows, BigDecimal.ZERO, date)
  }

  private fun hasNothingToAdd(
    currentValue: BigDecimal,
    realizedProfit: BigDecimal,
  ): Boolean = currentValue <= BigDecimal.ZERO && realizedProfit.signum() == 0

  private fun calculateCurrentValueForDate(
    currentHoldings: BigDecimal,
    instrument: Instrument,
    date: LocalDate,
    priceLookup: PriceLookup?,
  ): BigDecimal =
    when {
      currentHoldings <= BigDecimal.ZERO -> BigDecimal.ZERO
      instrument.isCash() -> currentHoldings
      isToday(date) ->
        currentHoldings.multiply(
          priceLookup?.pinnedPrice(instrument.id, date) ?: livePrice(instrument) ?: resolvePrice(instrument, date, priceLookup),
        )
      else -> currentHoldings.multiply(resolvePrice(instrument, date, priceLookup))
    }

  private fun resolvePrice(
    instrument: Instrument,
    date: LocalDate,
    priceLookup: PriceLookup?,
  ): BigDecimal =
    priceLookup?.priceOnOrBefore(instrument.id, date)
      ?: dailyPriceService.getPrice(instrument, date)
      ?: throw NoSuchElementException("No price found for ${instrument.symbol} on or before $date")

  private fun calculateRealizedProfit(transactions: List<PortfolioTransaction>): BigDecimal =
    InvestmentMath.calculateRealizedProfit(transactions)

  private fun calculateFallbackProfits(
    transactions: List<PortfolioTransaction>,
    currentValue: BigDecimal,
  ): Pair<BigDecimal, BigDecimal> = InvestmentMath.calculateFallbackProfits(transactions, currentValue)

  private fun updateMetrics(
    metrics: PortfolioMetrics,
    value: BigDecimal,
    realizedProfit: BigDecimal,
    unrealizedProfit: BigDecimal,
  ) {
    metrics.totalValue = metrics.totalValue.add(value)
    metrics.realizedProfit = metrics.realizedProfit.add(realizedProfit)
    metrics.unrealizedProfit = metrics.unrealizedProfit.add(unrealizedProfit)
    metrics.totalProfit = metrics.totalProfit.add(realizedProfit).add(unrealizedProfit)
  }
}
