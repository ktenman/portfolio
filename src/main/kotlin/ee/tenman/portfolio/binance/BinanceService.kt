package ee.tenman.portfolio.binance

import ee.tenman.portfolio.common.DailyPriceData
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.SortedMap
import java.util.TreeMap

@Service
class BinanceService(
  private val binanceClient: BinanceClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000))
  fun getDailyPrices(
    symbol: String,
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
  ): SortedMap<LocalDate, DailyPriceData> {
    log.info("Getting daily prices for symbol: $symbol")
    val dailyPrices = TreeMap<LocalDate, DailyPriceData>()
    try {
      var currentStartTime = startDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
      val endTime = endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

      while (true) {
        val klines =
          binanceClient.getKlines(
            symbol = symbol,
            interval = "1d",
            startTime = currentStartTime,
            endTime = endTime,
            limit = 1000, // Binance API typically limits to 1000 entries per request
          )

        if (klines.isEmpty()) break

        for (kline in klines) {
          val timestamp = kline[0].toLong()
          val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
          dailyPrices[date] =
            BinanceDailyPriceData(
              open = kline[1].toBigDecimal(),
              high = kline[2].toBigDecimal(),
              low = kline[3].toBigDecimal(),
              close = kline[4].toBigDecimal(),
              volume = kline[5].toBigDecimal().toLong(),
            )
        }

        // Prepare for the next iteration
        currentStartTime = klines.last()[0].toLong() + 1
        if (currentStartTime >= (endTime ?: Long.MAX_VALUE)) break
      }
    } catch (e: Exception) {
      log.error("Error getting daily prices for symbol: $symbol", e)
    }
    return dailyPrices
  }
}
