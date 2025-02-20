package ee.tenman.portfolio.ft

import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.common.DailyPriceDataImpl
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors

private val DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)

private val TICKERS: Map<String, String> = mapOf(
  "QDVE.DEX" to "93501088",
  "QDVE:GER:EUR" to "93501088",
)

private val REQUEST_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("yyyy/MM/dd")

@Service
class HistoricalPricesService(
  private val historicalPricesClient: HistoricalPricesClient
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun fetchPrices(symbol: String): Map<LocalDate, DailyPriceData> = runBlocking {
    // Create a dispatcher with 4 threads for parallel fetching.
    val dispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()

    // Prepare a list to hold all the one-year date ranges.
    val chunks = mutableListOf<Pair<String, String>>()
    var currentEndDate = LocalDate.now()
    var currentStartDate = currentEndDate.minusYears(1)

    // Generate chunks until we reach a reasonable lower bound (e.g. year 2000).
    while (true) {
      val formattedStart = currentStartDate.format(REQUEST_DATE_FORMATTER)
      val formattedEnd = currentEndDate.format(REQUEST_DATE_FORMATTER)
      chunks.add(formattedStart to formattedEnd)

      // Terminate if we've reached or passed our lower bound.
      if (currentStartDate.year <= 2000) break

      // Shift window one year back (subtract one day to avoid overlap)
      currentEndDate = currentStartDate.minusDays(1)
      currentStartDate = currentEndDate.minusYears(1)
    }

    // Launch parallel fetches for each chunk.
    val ticker = TICKERS[symbol] ?: symbol
    val deferredResults = chunks.map { (startDate, endDate) ->
      async(dispatcher) {
        fetchAndParsePrices(startDate, endDate, ticker)
      }
    }

    // Merge all results.
    val mergedResult = mutableMapOf<LocalDate, DailyPriceData>()
    deferredResults.awaitAll().forEach { partialResult ->
      mergedResult.putAll(partialResult)
    }

    // Clean up the dispatcher.
    dispatcher.close()
    mergedResult
  }

  fun fetchAndParsePrices(startDate: String, endDate: String, symbol: String): Map<LocalDate, DailyPriceData> {
    // Call the FT endpoint via Feign.
    val response = historicalPricesClient.getHistoricalPrices(startDate, endDate, symbol)
    val htmlContent = response.html.orEmpty()
    val wrappedHtml = "<table>$htmlContent</table>"
    val document = Jsoup.parse(wrappedHtml)
    val rows = document.select("tr")

    val result = mutableMapOf<LocalDate, DailyPriceData>()
    rows.forEach { row ->
      val cells = row.select("td")
      if (cells.size >= 6) {
        val dateText = cells[0].select("span").first()?.text() ?: cells[0].text()
        try {
          val date = LocalDate.parse(dateText, DATE_FORMATTER)
          val open = cells[1].text().replace(",", "").toBigDecimal()
          val high = cells[2].text().replace(",", "").toBigDecimal()
          val low = cells[3].text().replace(",", "").toBigDecimal()
          val close = cells[4].text().replace(",", "").toBigDecimal()

          val volumeStr = cells[5].text().replace(",", "").trim()
          val volume = if (volumeStr.endsWith("k", ignoreCase = true)) {
            volumeStr.dropLast(1).toBigDecimal().multiply(BigDecimal(1000)).toLong()
          } else {
            BigDecimal(volumeStr).toLong()
          }

          result[date] = DailyPriceDataImpl(open, high, low, close, volume)
        } catch (e: Exception) {
          log.error("Error parsing row with date '$dateText': ${e.message}")
        }
      }
    }
    return result
  }
}
