package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioTransaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode  // Added import for RoundingMode

@Service
class InvestmentMetricsService(
  private val unifiedProfitCalculationService: UnifiedProfitCalculationService
) {
  private companion object {
    private val log = LoggerFactory.getLogger(InvestmentMetricsService::class.java)
  }

  data class InstrumentMetrics(
    val totalInvestment: BigDecimal,
    val currentValue: BigDecimal,
    val profit: BigDecimal,
    val xirr: Double,
    val quantity: BigDecimal
  ) {
    override fun toString(): String =
      "InstrumentMetrics(totalInvestment=$totalInvestment, " +
        "currentValue=$currentValue, " +
        "profit=$profit, " +
        "xirr=${String.format("%.2f%%", xirr * 100)})"

    companion object {
      val EMPTY = InstrumentMetrics(
        totalInvestment = BigDecimal.ZERO,
        currentValue = BigDecimal.ZERO,
        profit = BigDecimal.ZERO,
        xirr = 0.0,
        quantity = BigDecimal.ZERO
      )
    }
  }

  fun calculateInstrumentMetrics(
    instrument: Instrument,
    transactions: List<PortfolioTransaction>
  ): InstrumentMetrics {
    if (transactions.isEmpty()) {
      return InstrumentMetrics.EMPTY
    }

    // Group transactions by platform
    val groupedByPlatform = transactions.groupBy { it.platform }

    var totalInvestment = BigDecimal.ZERO
    var totalHoldings = BigDecimal.ZERO
    var totalAverageWeightedCost = BigDecimal.ZERO

    // Calculate metrics for each platform separately
    groupedByPlatform.forEach { (_, platformTransactions) ->
      val (quantity, averageCost) = unifiedProfitCalculationService.calculateCurrentHoldings(platformTransactions)
      if (quantity > BigDecimal.ZERO) {
        val investment = quantity.multiply(averageCost)
        totalInvestment = totalInvestment.add(investment)
        totalHoldings = totalHoldings.add(quantity)

        // Keep track of average weighted cost for later profit calculation
        if (totalHoldings > BigDecimal.ZERO) {
          // Fixed: Use non-deprecated version of divide with explicit RoundingMode
          val weight = quantity.divide(totalHoldings, 10, RoundingMode.HALF_UP)
          totalAverageWeightedCost = totalAverageWeightedCost.add(averageCost.multiply(weight))
        }
      }
    }

    val currentPrice = instrument.currentPrice ?: BigDecimal.ZERO
    val currentValue = unifiedProfitCalculationService.calculateCurrentValue(totalHoldings, currentPrice)
    val profit = unifiedProfitCalculationService.calculateProfit(totalHoldings, totalAverageWeightedCost, currentPrice)

    // Calculate XIRR using the unified service
    val xirrTransactions = unifiedProfitCalculationService.buildXirrTransactions(transactions, currentValue)
    val xirr = unifiedProfitCalculationService.calculateAdjustedXirr(xirrTransactions, currentValue)

    return InstrumentMetrics(
      totalInvestment = totalInvestment,
      currentValue = currentValue,
      profit = profit,
      xirr = xirr,
      quantity = totalHoldings
    )
  }
}
