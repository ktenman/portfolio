package ee.tenman.portfolio.alphavantage

import ee.tenman.portfolio.alphavantage.AlphaVantageResponse.AlphaVantageDailyPriceData
import ee.tenman.portfolio.configuration.JsonMapperFactory
import jakarta.annotation.Resource
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class AlphaVantageService {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  }

  @Resource
  private lateinit var client: AlphaVantageClient

  fun getDailyTimeSeriesForLastWeek(symbol: String): Map<LocalDate, AlphaVantageDailyPriceData> {
    var adjustedSymbol = symbol
    if ("QDVE:GER:EUR" == symbol) {
      adjustedSymbol = "QDVE.DEX"
    }

    val ticker =
      getTicker(adjustedSymbol) ?: run {
        log.error("Failed to get ticker for symbol: $adjustedSymbol")
        return emptyMap()
      }

    return try {
      val timeSeriesDaily = client.getTimeSeries("TIME_SERIES_DAILY", ticker)
      val json = JsonMapperFactory.instance.writeValueAsString(timeSeriesDaily)
      log.info("Retrieved daily ticker data for $ticker: ${JsonMapperFactory.truncateJson(json)}")

      timeSeriesDaily.dailyTimeSeries
        ?.asSequence()
        ?.associate { (dateString, data) ->
          LocalDate.parse(dateString, DATE_FORMATTER) to data
        }?.toMap() ?: emptyMap()
    } catch (e: Exception) {
      log.error("Error fetching daily data from Alpha Vantage for ticker $ticker", e)
      emptyMap()
    }
  }

  fun getTicker(search: String): String? {
    val symbolSearch = client.getSearch("SYMBOL_SEARCH", search, "1")
    return symbolSearch.bestMatches?.firstOrNull()?.symbol
  }
}
