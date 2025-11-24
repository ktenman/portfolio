package ee.tenman.portfolio.lightyear

import ee.tenman.portfolio.common.DailyPriceData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class LightyearHistoricalPricesService(
  private val lightyearPriceClient: LightyearPriceClient,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Retryable(backoff = Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  fun fetchHistoricalPrices(uuid: String): Map<LocalDate, DailyPriceData> =
    runBlocking {
      log.info("Fetching historical prices for Lightyear instrument: {}", uuid)

      val (fiveYearData, maxData) =
        listOf(
          async { fetchChartData(uuid, "5y") },
          async { fetchChartData(uuid, "max") },
        ).awaitAll().let { it[0] to it[1] }

      val mergedData = mutableMapOf<LocalDate, DailyPriceData>()
      mergedData.putAll(maxData)
      mergedData.putAll(fiveYearData)

      log.info(
        "Fetched {} historical prices for instrument {} (5y: {}, max: {})",
        mergedData.size,
        uuid,
        fiveYearData.size,
        maxData.size,
      )

      mergedData
    }

  private fun fetchChartData(
    uuid: String,
    range: String,
  ): Map<LocalDate, DailyPriceData> {
    val path = "/v1/market-data/$uuid/chart?range=$range"
    val response = lightyearPriceClient.getChartData(path)

    return response
      .filter { isValidDataPoint(it) }
      .associate { dataPoint ->
        val date = parseTimestamp(dataPoint.timestamp)
        date to
          LightyearDailyPriceData(
            open = dataPoint.open,
            high = dataPoint.high,
            low = dataPoint.low,
            close = dataPoint.close,
            volume = dataPoint.volume,
          )
      }
  }

  private fun isValidDataPoint(dataPoint: LightyearChartDataPoint): Boolean =
    dataPoint.high > BigDecimal.ZERO && dataPoint.low > BigDecimal.ZERO

  private fun parseTimestamp(timestamp: String): LocalDate =
    ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME).toLocalDate()
}
