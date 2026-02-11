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
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class BinanceService(
  private val binanceClient: BinanceClient,
  private val clock: Clock = Clock.systemDefaultZone(),
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val OPEN_TIME = 0
    private const val OPEN_PRICE = 1
    private const val HIGH_PRICE = 2
    private const val LOW_PRICE = 3
    private const val CLOSE_PRICE = 4
    private const val VOLUME = 5
  }

  @Retryable(backoff = Backoff(delay = 1000))
  fun getCurrentPrice(symbol: String): BigDecimal {
    log.debug("Getting current price for symbol: $symbol")
    val tickerPrice = binanceClient.getTickerPrice(symbol)
    return tickerPrice.price.toBigDecimal()
  }

  fun getDailyPricesIncremental(
    symbol: String,
    lastStoredDate: LocalDate?,
  ): SortedMap<LocalDate, DailyPriceData> {
    val now = LocalDate.now(clock)
    val startDate = lastStoredDate?.plusDays(1) ?: LocalDate.of(2015, 1, 1)
    if (!startDate.isBefore(now)) {
      log.info("No new data to fetch for $symbol, already up to date")
      return TreeMap()
    }
    log.info("Fetching $symbol data from $startDate to $now (incremental)")
    return getDailyPrices(symbol, startDate, now.plusDays(1))
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
            TreeMap()
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
  fun getHourlyPrices(
    symbol: String,
    hours: Long = 48,
  ): SortedMap<Instant, BigDecimal> {
    log.info("Getting hourly prices for $symbol (last $hours hours)")
    val now = Instant.now(clock)
    val startTime = now.minus(hours, ChronoUnit.HOURS).toEpochMilli()
    val endTime = now.toEpochMilli()
    val klines =
      binanceClient.getKlines(
        symbol = symbol,
        interval = "1h",
        startTime = startTime,
        endTime = endTime,
        limit = 1000,
      )
    val result =
      klines.associateTo(TreeMap()) { kline ->
        val hour = Instant.ofEpochMilli(kline[OPEN_TIME].toLong()).truncatedTo(ChronoUnit.HOURS)
        hour to kline[CLOSE_PRICE].toBigDecimal()
      }
    log.info("Fetched ${result.size} hourly prices for $symbol")
    return result
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
            limit = 1000,
          )

        if (klines.isNotEmpty()) {
          for (kline in klines) {
            val timestamp = kline[OPEN_TIME].toLong()
            val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            dailyPrices[date] =
              BinanceDailyPriceData(
                open = kline[OPEN_PRICE].toBigDecimal(),
                high = kline[HIGH_PRICE].toBigDecimal(),
                low = kline[LOW_PRICE].toBigDecimal(),
                close = kline[CLOSE_PRICE].toBigDecimal(),
                volume = kline[VOLUME].toBigDecimal().setScale(0, RoundingMode.DOWN).toLong(),
              )
          }

          currentStartTime = klines.last()[OPEN_TIME].toLong() + 1
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
