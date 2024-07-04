package ee.tenman.portfolio.service.xirr

import ee.tenman.portfolio.alphavantage.AlphaVantageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class XirrService(private val alphaVantageService: AlphaVantageService) {
  private val log = LoggerFactory.getLogger(XirrService::class.java)

  companion object {
    private const val BASE_MONTHLY_INVESTMENT = 3000.00
  }

  fun calculateStockXirr(ticker: String): Double = runCatching {
    val historicalData = getHistoricalData(ticker)
    val transactions = processHistoricalData(historicalData)
    val xirrValue = Xirr(transactions).calculate()
    log.info("$ticker : ${String.format("%,.3f%%", xirrValue * 100)}")
    xirrValue + 1
  }.getOrElse {
    log.error("Error in calculating XIRR for ticker: $ticker", it)
    Double.NaN
  }

  private fun getHistoricalData(ticker: String): SortedMap<LocalDate, BigDecimal> {
    return alphaVantageService.getMonthlyTimeSeries(ticker)
      .mapValues { it.value.close }
      .toSortedMap()
  }

  private fun processHistoricalData(historicalData: SortedMap<LocalDate, BigDecimal>): List<Transaction> {
    val transactions = mutableListOf<Transaction>()
    var totalBoughtStocksCount = BigDecimal.ZERO
    var lastMonthDate: LocalDate? = null

    historicalData.forEach { (date, price) ->
      if (shouldAddStockPurchase(date, lastMonthDate)) {
        val stocksCount = BigDecimal(BASE_MONTHLY_INVESTMENT).divide(price, RoundingMode.DOWN)
        totalBoughtStocksCount += stocksCount
        transactions.add(Transaction(-BASE_MONTHLY_INVESTMENT, date))
        lastMonthDate = date
      }

      if (date == historicalData.lastKey()) {
        val amount = price.multiply(totalBoughtStocksCount).toDouble()
        transactions.add(Transaction(amount, date))
      }
    }

    return transactions
  }

  private fun shouldAddStockPurchase(currentDate: LocalDate, lastMonthDate: LocalDate?): Boolean {
    return lastMonthDate == null || ChronoUnit.MONTHS.between(
      lastMonthDate.withDayOfMonth(1),
      currentDate.withDayOfMonth(1)
    ) >= 1
  }
}
