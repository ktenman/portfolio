package ee.tenman.portfolio.ecb

import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate

object EcbCsvParser {
  private val log = LoggerFactory.getLogger(javaClass)

  fun parse(csv: String): List<EcbDailyRate> {
    val lines = csv.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()
    val columns = lines.first().split(",")
    val dateIndex = columns.indexOf("TIME_PERIOD")
    val rateIndex = columns.indexOf("OBS_VALUE")
    if (dateIndex < 0 || rateIndex < 0) return emptyList()
    return lines.drop(1).mapNotNull { parseRow(it, dateIndex, rateIndex) }
  }

  private fun parseRow(
    line: String,
    dateIndex: Int,
    rateIndex: Int,
  ): EcbDailyRate? {
    val cells = line.split(",")
    if (cells.size <= maxOf(dateIndex, rateIndex)) return null
    if (cells[rateIndex].isBlank()) return null
    return runCatching { EcbDailyRate(LocalDate.parse(cells[dateIndex]), BigDecimal(cells[rateIndex])) }
      .onFailure { log.warn("Skipping malformed ECB rate row '$line'") }
      .getOrNull()
  }
}
