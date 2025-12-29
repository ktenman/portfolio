package ee.tenman.portfolio.service.calculation

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.model.metrics.InstrumentMetrics
import ee.tenman.portfolio.model.metrics.PortfolioMetrics
import ee.tenman.portfolio.service.pricing.DailyPriceService
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

  fun calculateCurrentHoldings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, BigDecimal> =
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
  ): InstrumentMetrics {
    if (transactions.isEmpty()) return InstrumentMetrics.EMPTY
    val effectiveDate = calculationDate ?: LocalDate.now(clock)
    val (totalHoldings, totalInvestment) = holdingsCalculationService.calculateAggregatedHoldings(transactions)
    val currentPrice = instrument.currentPrice ?: BigDecimal.ZERO
    val currentValue = holdingsCalculationService.calculateCurrentValue(totalHoldings, currentPrice)
    val realizedProfit = calculateRealizedProfit(transactions)
    val unrealizedProfit = currentValue.subtract(totalInvestment)
    val cashFlows = xirrCalculationService.buildCashFlows(transactions, currentValue, effectiveDate)
    return InstrumentMetrics(
      totalInvestment = totalInvestment,
      currentValue = currentValue,
      profit = realizedProfit.add(unrealizedProfit),
      realizedProfit = realizedProfit,
      unrealizedProfit = unrealizedProfit,
      xirr = xirrCalculationService.calculateAdjustedXirr(cashFlows, effectiveDate),
      quantity = totalHoldings,
    )
  }

  fun calculateInstrumentMetricsWithProfits(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    calculationDate: LocalDate? = null,
  ): InstrumentMetrics {
    if (transactions.isEmpty()) return InstrumentMetrics.EMPTY
    val effectiveDate = calculationDate ?: LocalDate.now(clock)
    val currentPrice = instrument.currentPrice ?: BigDecimal.ZERO
    transactionService.calculateTransactionProfits(transactions, currentPrice)
    return calculateInstrumentMetrics(instrument, transactions, effectiveDate)
  }

  fun calculatePortfolioMetrics(
    instrumentGroups: Map<Instrument, List<PortfolioTransaction>>,
    date: LocalDate,
  ): PortfolioMetrics {
    val metrics = PortfolioMetrics()
    transactionService.calculateTransactionProfits(instrumentGroups.values.flatten())
    instrumentGroups.forEach { (instrument, transactions) ->
      runCatching {
        processInstrumentWithUnifiedCalculation(instrument, transactions, date, metrics)
      }.onFailure {
        log.warn("Unified calculation failed, using fallback: ${it.message}")
        processInstrumentWithFallback(instrument, transactions, date, metrics)
      }
    }
    return metrics
  }

  private fun processInstrumentWithUnifiedCalculation(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    metrics: PortfolioMetrics,
  ) {
    val (currentHoldings, totalInvestment) = holdingsCalculationService.calculateAggregatedHoldings(transactions)
    val realizedProfit = calculateRealizedProfit(transactions)
    val currentValue = calculateCurrentValueForDate(currentHoldings, instrument, date)
    val unrealizedProfit = currentValue.subtract(totalInvestment)
    if (currentValue <= BigDecimal.ZERO && realizedProfit <= BigDecimal.ZERO) return
    updateMetrics(metrics, currentValue, realizedProfit, unrealizedProfit)
    xirrCalculationService.addCashFlows(metrics.xirrCashFlows, transactions, currentValue, date)
  }

  private fun processInstrumentWithFallback(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    metrics: PortfolioMetrics,
  ) {
    val netQuantity = holdingsCalculationService.calculateNetQuantity(transactions)
    if (netQuantity <= BigDecimal.ZERO) {
      processZeroQuantityFallback(transactions, date, metrics)
      return
    }
    val price =
      runCatching { dailyPriceService.getPrice(instrument, date) }
        .onFailure { log.warn("Skipping ${instrument.symbol} on $date: ${it.message}") }
        .getOrNull() ?: return
    val currentValue = netQuantity.multiply(price)
    val (realizedProfit, unrealizedProfit) = calculateFallbackProfits(transactions, currentValue)
    if (currentValue <= BigDecimal.ZERO && realizedProfit <= BigDecimal.ZERO) return
    updateMetrics(metrics, currentValue, realizedProfit, unrealizedProfit)
    xirrCalculationService.addCashFlows(metrics.xirrCashFlows, transactions, currentValue, date)
  }

  private fun processZeroQuantityFallback(
    transactions: List<PortfolioTransaction>,
    date: LocalDate,
    metrics: PortfolioMetrics,
  ) {
    val (realizedProfit, unrealizedProfit) = calculateFallbackProfits(transactions, BigDecimal.ZERO)
    if (realizedProfit <= BigDecimal.ZERO) return
    updateMetrics(metrics, BigDecimal.ZERO, realizedProfit, unrealizedProfit)
    xirrCalculationService.addCashFlows(metrics.xirrCashFlows, transactions, BigDecimal.ZERO, date)
  }

  private fun calculateCurrentValueForDate(
    currentHoldings: BigDecimal,
    instrument: Instrument,
    date: LocalDate,
  ): BigDecimal =
    when {
      currentHoldings <= BigDecimal.ZERO -> BigDecimal.ZERO
      isToday(date) -> currentHoldings.multiply(instrument.currentPrice ?: BigDecimal.ZERO)
      else -> currentHoldings.multiply(dailyPriceService.getPrice(instrument, date))
    }

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
