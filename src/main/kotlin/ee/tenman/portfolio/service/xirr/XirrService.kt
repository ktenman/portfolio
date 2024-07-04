package ee.tenman.portfolio.service.xirr

import ee.tenman.portfolio.alphavantage.AlphaVantageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class XirrService(private val alphaVantageService: AlphaVantageService) {
  private val log = LoggerFactory.getLogger(XirrService::class.java)
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
}
