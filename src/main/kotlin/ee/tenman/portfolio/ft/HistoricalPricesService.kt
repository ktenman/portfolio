package ee.tenman.portfolio.ft

import ee.tenman.portfolio.common.DailyPriceData
import ee.tenman.portfolio.common.DailyPriceDataImpl
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors

private val DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)

private val TICKERS: Map<String, String> =
  mapOf(
    "QDVE.DEX" to "93501088",
    "QDVE:GER:EUR" to "93500326",
    "XAIX:GER:EUR" to "515873934",
    "VUAA:GER:EUR" to "573788032",
    "SPYL:GER:EUR" to "842646185",
    "WTAI:MIL:EUR" to "505821605",
    "CSX5:AEX:EUR" to "28989466",
    "VNRT:AEX:EUR" to "79451207",
    "GB00B0ZDNB53:GBP" to "543017012",
    "VWCE:GER:EUR" to "544541677",
    "SPPW:GER:EUR" to "519953640",
    "VNRA:GER:EUR" to "544523562",
    "XNAS:GER:EUR" to "640687109",
    "EXUS:GER:EUR" to "871264993",
    "DFEN:GER:EUR" to "794499387",
    "WBIT:GER:EUR" to "653421505",
    "DFND:PAR:EUR" to "913527044",
  )

private val REQUEST_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("yyyy/MM/dd")

@Service
class HistoricalPricesService(
  private val historicalPricesClient: HistoricalPricesClient,
  private val clock: Clock,
  @Value("\${ft.parallel.threads:5}") private val parallelThreads: Int = 5,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val dispatcher = Executors.newFixedThreadPool(parallelThreads).asCoroutineDispatcher()

  fun fetchPrices(symbol: String): Map<LocalDate, DailyPriceData> =
    runBlocking {
      val chunks = mutableListOf<Pair<String, String>>()
      var currentEndDate = LocalDate.now(clock)
      var currentStartDate = currentEndDate.minusYears(1)

      while (true) {
        val formattedStart = currentStartDate.format(REQUEST_DATE_FORMATTER)
        val formattedEnd = currentEndDate.format(REQUEST_DATE_FORMATTER)
        chunks.add(formattedStart to formattedEnd)

        if (currentStartDate.year <= 2015) break

        currentEndDate = currentStartDate.minusDays(1)
        currentStartDate = currentEndDate.minusYears(1)
      }

      val ticker = TICKERS[symbol] ?: symbol
      val deferredResults =
        chunks.map { (startDate, endDate) ->
          async(dispatcher) {
            fetchAndParsePrices(startDate, endDate, ticker)
          }
        }

      val mergedResult = mutableMapOf<LocalDate, DailyPriceData>()
      deferredResults.awaitAll().forEach { partialResult ->
        mergedResult.putAll(partialResult)
      }

      mergedResult
    }

  fun fetchAndParsePrices(
    startDate: String,
    endDate: String,
    symbol: String,
  ): Map<LocalDate, DailyPriceData> {
    val response = historicalPricesClient.getHistoricalPrices(startDate, endDate, symbol)
    val htmlContent = response.html.orEmpty()
    val wrappedHtml = "<table>$htmlContent</table>"
    val document = Jsoup.parse(wrappedHtml)
    val rows = document.select("tr")

    val result = mutableMapOf<LocalDate, DailyPriceData>()
    rows.forEach { row ->
      val cells = row.select("td")
      if (cells.size < 6) return@forEach

      val dateText = cells[0].select("span").first()?.text() ?: cells[0].text()
      try {
        val date = LocalDate.parse(dateText, DATE_FORMATTER)
        val open = cells[1].text().replace(",", "").toBigDecimal()
        val high = cells[2].text().replace(",", "").toBigDecimal()
        val low = cells[3].text().replace(",", "").toBigDecimal()
        val close = cells[4].text().replace(",", "").toBigDecimal()

        val volumeStr = cells[5].text().replace(",", "").trim()
        val volume =
          when {
          volumeStr.endsWith("m", ignoreCase = true) ->
            volumeStr
              .dropLast(1)
              .toBigDecimal()
              .multiply(BigDecimal(1_000_000))
              .toLong()
          volumeStr.endsWith("k", ignoreCase = true) ->
            volumeStr
              .dropLast(1)
              .toBigDecimal()
              .multiply(BigDecimal(1_000))
              .toLong()
          volumeStr.endsWith("b", ignoreCase = true) ->
            volumeStr
              .dropLast(1)
              .toBigDecimal()
              .multiply(BigDecimal(1_000_000_000))
              .toLong()
          else -> BigDecimal(volumeStr).toLong()
        }

        result[date] = DailyPriceDataImpl(open, high, low, close, volume)
      } catch (e: Exception) {
        log.error("Error parsing row with date '$dateText': ${e.message}")
      }
    }
    return result
  }
}
