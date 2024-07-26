package ee.tenman.portfolio.alphavantage

import com.google.gson.Gson
import ee.tenman.portfolio.alphavantage.AlphaVantageResponse.AlphaVantageDayData
import jakarta.annotation.Resource
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Service
class AlphaVantageService {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private val GSON = Gson()
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  }

  @Resource
  private lateinit var client: AlphaVantageClient

  @Retryable(backoff = Backoff(delay = 1000))
  fun getMonthlyTimeSeries(symbol: String): Map<LocalDate, AlphaVantageResponse.AlphaVantageDayData> {
    val ticker = getTicker(symbol) ?: throw RuntimeException("Failed to get ticker for symbol: $symbol")

    return try {
      val timeSeriesMonthly = client.getTimeSeries("TIME_SERIES_MONTHLY", ticker)
      log.info("Retrieved monthly ticker data for $ticker: ${GSON.toJson(timeSeriesMonthly)}")

      timeSeriesMonthly.monthlyTimeSeries?.asSequence()
        ?.associate { (dateString, data) ->
          YearMonth.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atEndOfMonth() to data
        }?.toMap()
        ?: emptyMap()
    } catch (e: Exception) {
      log.error("Error fetching monthly data from Alpha Vantage for ticker $ticker", e)
      throw RuntimeException("Error fetching monthly data from Alpha Vantage for ticker $ticker", e)
    }
  }

  @Retryable(backoff = Backoff(delay = 1000))
  fun getDailyTimeSeriesForLastWeek(symbol: String): Map<LocalDate, AlphaVantageDayData> {
    var adjustedSymbol = symbol
    if ("QDVE:GER:EUR" == symbol) {
      adjustedSymbol = "QDVE.DEX"
    }
    val ticker = getTicker(adjustedSymbol) ?: throw RuntimeException("Failed to get ticker for symbol: $adjustedSymbol")

    return try {
      val timeSeriesDaily = client.getTimeSeries("TIME_SERIES_DAILY", ticker)
      log.info("Retrieved daily ticker data for $ticker: ${GSON.toJson(timeSeriesDaily)}")

      timeSeriesDaily.dailyTimeSeries?.asSequence()
        ?.associate { (dateString, data) ->
          LocalDate.parse(dateString, DATE_FORMATTER) to data
        }?.toMap()
        ?: emptyMap()
    } catch (e: Exception) {
      log.error("Error fetching daily data from Alpha Vantage for ticker $ticker", e)
      throw RuntimeException("Error fetching daily data from Alpha Vantage for ticker $ticker", e)
    }
  }

  @Retryable(backoff = Backoff(delay = 1000))
  fun getTicker(search: String): String? {
    val symbolSearch = client.getSearch("SYMBOL_SEARCH", search, "1")
    return symbolSearch.bestMatches?.firstOrNull()?.symbol
  }
}
