package ee.tenman.portfolio.binance

import ee.tenman.portfolio.common.DailyPriceData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@Service
class BinanceService(
  private val binanceClient: BinanceClient,
  private val clock: Clock = Clock.systemDefaultZone(),
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000))
  fun getCurrentPrice(symbol: String): BigDecimal {
    log.info("Getting current price for symbol: {}", symbol)
    val tickerPrice = binanceClient.getTickerPrice(symbol)
    return tickerPrice.price.toBigDecimal()
  }

  fun getDailyPricesAsync(symbol: String): SortedMap<LocalDate, DailyPriceData> =
    runBlocking {
      val now = LocalDate.now(clock)
      val yearChunks = mutableListOf<Pair<LocalDate, LocalDate>>()
      var currentEnd = now.plusDays(1)
      var currentStart = currentEnd.minusYears(1)

      while (currentStart.year >= 2015) {
        yearChunks.add(currentStart to currentEnd)
        currentEnd = currentStart.minusDays(1)
        currentStart = currentEnd.minusYears(1)
      }

      log.info("Fetching $symbol data in ${yearChunks.size} yearly chunks using async")

      val deferredResults =
        yearChunks.map { (startDate, endDate) ->
        async {
          try {
            getDailyPrices(symbol, startDate, endDate)
          } catch (e: Exception) {
            log.warn("Failed to fetch $symbol for period $startDate to $endDate: ${e.message}")
            emptyMap()
          }
        }
      }

      val mergedResult = TreeMap<LocalDate, DailyPriceData>()
      deferredResults.awaitAll().forEach { partialResult ->
        mergedResult.putAll(partialResult)
      }

      log.info("Completed async fetch for $symbol: ${mergedResult.size} total data points")
      mergedResult
    }

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

      var shouldContinue = true
      while (shouldContinue) {
        val klines =
          binanceClient.getKlines(
            symbol = symbol,
            interval = "1d",
            startTime = currentStartTime,
            endTime = endTime,
            limit = 1000, // Binance API typically limits to 1000 entries per request
          )

        if (klines.isNotEmpty()) {
          for (kline in klines) {
            val timestamp = kline[0].toLong()
            val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            dailyPrices[date] =
              BinanceDailyPriceData(
                open = kline[1].toBigDecimal(),
                high = kline[2].toBigDecimal(),
                low = kline[3].toBigDecimal(),
                close = kline[4].toBigDecimal(),
                volume = kline[5].toBigDecimal().setScale(0, RoundingMode.DOWN).toLong(),
              )
          }

          currentStartTime = klines.last()[0].toLong() + 1
          shouldContinue = currentStartTime < (endTime ?: Long.MAX_VALUE)
        } else {
          shouldContinue = false
        }
      }
    } catch (e: Exception) {
      log.error("Error getting daily prices for symbol: $symbol", e)
      throw e
    }
    return dailyPrices
  }
}
