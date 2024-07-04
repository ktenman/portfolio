package ee.tenman.portfolio.service

import com.google.gson.Gson
import ee.tenman.portfolio.alphavantage.AlphaVantageClient
import ee.tenman.portfolio.alphavantage.AlphaVantageResponse
import ee.tenman.portfolio.alphavantage.AlphaVantageResponse.AlphaVantageDayData
import jakarta.annotation.Resource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Service
class AlphaVantageService {

  companion object {
    private val log: Logger = LoggerFactory.getLogger(AlphaVantageService::class.java)
    private val GSON = Gson()
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  }

  @Resource
  private lateinit var client: AlphaVantageClient

  @Retryable(backoff = Backoff(delay = 1000))
  fun getMonthlyTimeSeries(symbol: String): Map<LocalDate, AlphaVantageResponse.AlphaVantageDayData> {
    val ticker = getTicker(symbol) ?: throw RuntimeException("Failed to get ticker for symbol: $symbol")

    return try {
      val timeSeriesMonthly = client.getMonthlyTimeSeries("TIME_SERIES_MONTHLY", ticker)
      log.info("Retrieved monthly ticker data for $ticker: ${GSON.toJson(timeSeriesMonthly)}")

      timeSeriesMonthly.monthlyTimeSeries?.asSequence()
        ?.associate { (dateString, data) ->
          YearMonth.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM"))
            .atEndOfMonth() to data
        }
        ?.toMap()
        ?: emptyMap()
    } catch (e: Exception) {
      log.error("Error fetching monthly data from Alpha Vantage for ticker $ticker", e)
      throw RuntimeException("Error fetching monthly data from Alpha Vantage for ticker $ticker", e)
    }
  }

  @Retryable(backoff = Backoff(delay = 1000))
  fun getDailyTimeSeriesForLastWeek(symbol: String): Map<LocalDate, AlphaVantageDayData> {
    val ticker = getTicker(symbol) ?: throw RuntimeException("Failed to get ticker for symbol: $symbol")

    return try {
      val timeSeriesDaily = client.getDailyTimeSeries("TIME_SERIES_DAILY", ticker)
      log.info("Retrieved daily ticker data for $ticker: ${GSON.toJson(timeSeriesDaily)}")

      val today = LocalDate.now()
      val lastWeek = today.minusWeeks(1)

      timeSeriesDaily.dailyTimeSeries?.asSequence()
        ?.associate { (dateString, data) ->
          LocalDate.parse(dateString, DATE_FORMATTER) to data
        }
        ?.filterKeys { date -> date.isAfter(lastWeek) && !date.isAfter(today) }
        ?.toMap()
        ?: emptyMap()
    } catch (e: Exception) {
      log.error("Error fetching daily data from Alpha Vantage for ticker $ticker", e)
      throw RuntimeException("Error fetching daily data from Alpha Vantage for ticker $ticker", e)
    }
  }

  @Retryable(backoff = Backoff(delay = 1000))
  fun getTodayData(symbol: String): AlphaVantageDayData? {
    return getDailyTimeSeriesForLastWeek(symbol)[LocalDate.now()]
  }

  @Retryable(backoff = Backoff(delay = 1000))
  fun getTicker(search: String): String? {
    val symbolSearch = client.getSearch("SYMBOL_SEARCH", search, "1")
    return symbolSearch.bestMatches?.firstOrNull()?.symbol
  }
}
