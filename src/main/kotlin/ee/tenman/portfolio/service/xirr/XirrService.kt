package ee.tenman.portfolio.service.xirr

import ee.tenman.portfolio.alphavantage.AlphaVantageService
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PortfolioTransaction
import ee.tenman.portfolio.domain.TransactionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class XirrService(private val alphaVantageService: AlphaVantageService) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val baseMonthlyInvestment = BigDecimal(3000.00)

  fun calculateStockXirr(ticker: String): Double = runCatching {
    val historicalData = alphaVantageService.getMonthlyTimeSeries(ticker)
      .mapValues { it.value.close }
      .toSortedMap()

    var totalBoughtStocksCount = BigDecimal.ZERO
    var lastMonthDate: LocalDate? = null

    val transactions = historicalData.mapNotNull { (date, price) ->
      if (lastMonthDate == null || ChronoUnit.MONTHS.between(lastMonthDate, date) >= 1) {
        val stocksCount = baseMonthlyInvestment.divide(price, RoundingMode.DOWN)
        totalBoughtStocksCount += stocksCount
        lastMonthDate = date
        Transaction(-baseMonthlyInvestment.toDouble(), date)
      } else null
    } + Transaction(
      historicalData.values.last().multiply(totalBoughtStocksCount).toDouble(),
      historicalData.keys.last()
    )

    val xirrValue = Xirr(transactions).calculate()
    log.info("$ticker : ${String.format("%,.3f%%", xirrValue * 100)}")
    xirrValue + 1
  }.getOrElse {
    log.error("Error in calculating XIRR for ticker: $ticker", it)
    Double.NaN
  }

  fun calculateInvestmentAndHoldings(transactions: List<PortfolioTransaction>): Pair<BigDecimal, Map<Instrument, BigDecimal>> {
    var totalInvestment = BigDecimal.ZERO
    val holdings = mutableMapOf<Instrument, BigDecimal>()

    // Group transactions by instrument
    val transactionsByInstrument = transactions.groupBy { it.instrument }

    transactionsByInstrument.forEach { (instrument, instrumentTransactions) ->
      var currentHolding = BigDecimal.ZERO
      var investmentForInstrument = BigDecimal.ZERO

      // Process transactions in chronological order
      instrumentTransactions.sortedBy { it.transactionDate }.forEach { transaction ->
        val amount = transaction.price * transaction.quantity
        when (transaction.transactionType) {
          TransactionType.BUY -> {
            currentHolding += transaction.quantity
            // Only count investment if we still hold some position
            investmentForInstrument += amount
          }
          TransactionType.SELL -> {
            val sellRatio = transaction.quantity.divide(currentHolding, 10, RoundingMode.HALF_UP)
            investmentForInstrument = investmentForInstrument.multiply(BigDecimal.ONE.subtract(sellRatio))
            currentHolding -= transaction.quantity
          }
        }
      }

      // Only count instruments with remaining positions
      if (currentHolding > BigDecimal.ZERO) {
        holdings[instrument] = currentHolding
        totalInvestment += investmentForInstrument
      }
    }

    log.info("Calculated total investment: $totalInvestment, Holdings: $holdings")
    return Pair(totalInvestment, holdings)
  }
}
