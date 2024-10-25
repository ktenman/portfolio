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

    transactions.forEach { transaction ->
      val amount = transaction.price * transaction.quantity
      totalInvestment += if (transaction.transactionType == TransactionType.BUY) amount else -amount

      val currentHolding = holdings.getOrDefault(transaction.instrument, BigDecimal.ZERO)
      val newHolding = when (transaction.transactionType) {
        TransactionType.BUY -> currentHolding + transaction.quantity
        TransactionType.SELL -> currentHolding - transaction.quantity
      }
      holdings[transaction.instrument] = newHolding
    }

    log.info("Total Investment: $totalInvestment, Holdings: $holdings")
    return Pair(totalInvestment, holdings)
  }
}
