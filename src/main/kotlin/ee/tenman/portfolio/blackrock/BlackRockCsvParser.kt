package ee.tenman.portfolio.blackrock

import org.slf4j.LoggerFactory
import java.math.BigDecimal

object BlackRockCsvParser {
  private val log = LoggerFactory.getLogger(javaClass)
  private const val EQUITY = "Equity"
  private val REQUIRED = listOf("Ticker", "Name", "Sector", "Asset Class", "Weight (%)")

  fun parse(csv: String): List<BlackRockHolding> {
    val lines = csv.lines().filter { it.isNotBlank() }
    val headerIndex = lines.indexOfFirst { it.startsWith("Ticker,") }
    check(headerIndex >= 0) { "BlackRock holdings CSV missing 'Ticker,' header row" }
    val columns = splitCsvLine(lines[headerIndex])
    val indices = REQUIRED.associateWith { columns.indexOf(it) }
    check(indices.values.all { it >= 0 }) { "BlackRock holdings CSV missing required columns, found: $columns" }
    return lines.drop(headerIndex + 1).mapNotNull { parseRow(it, indices) }
  }

  private fun parseRow(
    line: String,
    indices: Map<String, Int>,
  ): BlackRockHolding? {
    val cells = splitCsvLine(line)
    if (cells.size <= indices.values.max()) return null
    if (cells[indices.getValue("Asset Class")].trim() != EQUITY) return null
    return runCatching {
      BlackRockHolding(
        ticker = cells[indices.getValue("Ticker")].trim().ifBlank { null },
        name = cells[indices.getValue("Name")].trim(),
        sector = cells[indices.getValue("Sector")].trim().ifBlank { null },
        weight = BigDecimal(cells[indices.getValue("Weight (%)")].trim()),
      )
    }.onFailure { log.warn("Skipping malformed BlackRock holding row '$line'") }.getOrNull()
  }

  private fun splitCsvLine(line: String): List<String> {
    val result =
      line.fold(Triple(emptyList<String>(), "", false)) { (fields, current, inQuotes), char ->
        when {
          char == '"' -> Triple(fields, current, !inQuotes)
          char == ',' && !inQuotes -> Triple(fields + current, "", inQuotes)
          else -> Triple(fields, current + char, inQuotes)
        }
      }
    return result.first + result.second
  }
}
