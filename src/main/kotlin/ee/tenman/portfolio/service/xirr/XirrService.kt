package ee.tenman.portfolio.service.xirr

import ee.tenman.portfolio.alphavantage.AlphaVantageService
import ee.tenman.portfolio.service.OnceInstrumentDataRetrievalService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@Service
class XirrService(
  private val alphaVantageService: AlphaVantageService
) {
  val log: Logger = LoggerFactory.getLogger(XirrService::class.java)

  companion object {
    private val log = LoggerFactory.getLogger(OnceInstrumentDataRetrievalService::class.java)
    private val BASE_ORIGINAL_BIG_DECIMAL_STOCK = BigDecimal("3000.00")
  }

  fun getHistoricalData(ticker: String): SortedMap<LocalDate, BigDecimal> =
    alphaVantageService.getMonthlyTimeSeries(ticker)
      .mapValues { it.value.close }
      .toSortedMap()

  fun calculateStockXirr(ticker: String): Double = runCatching {
    val transactions = processHistoricalData(ticker)
    val xirrValue = Xirr(transactions).xirr()
    log.info("$ticker : ${String.format("%,.3f%%", xirrValue * 100)}")
    xirrValue + 1
  }.getOrElse {
    log.error("Error in calculating XIRR for ticker: $ticker", it)
    Double.NaN
  }

  private fun processHistoricalData(ticker: String): List<Transaction> {
    val historicalData = getHistoricalData(ticker)
    val lastDataDate = historicalData.lastKey()

    return TransactionCalculator(BASE_ORIGINAL_BIG_DECIMAL_STOCK).apply {
      historicalData.forEach { (date, price) -> processDate(date, price, lastDataDate) }
    }.getTransactions()
  }

}
