package ee.tenman.portfolio.tuleva

import ee.tenman.portfolio.blackrock.BlackRockFund
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class TulevaAllocationRow(
  val isin: String,
  val name: String,
  val weight: BigDecimal,
  val repairedFrom: String?,
)

data class TulevaAllocationReport(
  val asOfDate: LocalDate,
  val reportedTotal: BigDecimal,
  val rows: List<TulevaAllocationRow>,
)

val TULEVA_PROXIES =
  mapOf(
    "IE00BFG1TM61" to BlackRockFund.SAWD,
    "IE0009FT4LX4" to BlackRockFund.SAWD,
    "IE000QWCYQT0" to BlackRockFund.SAWD,
    "IE000I9HGDZ3" to BlackRockFund.SAWD,
    "IE00BKPTWY98" to BlackRockFund.SAEM,
  )

object TulevaAllocationParser {
  private val DATE = Regex("(?:seisuga|aruanne)\\s+(\\d{2}\\.\\d{2}\\.\\d{4})")
  private val FUND_ROW = Regex("\\b([A-Z]{2}[A-Z0-9]{9,10})\\s+[A-Z]{2}\\s+EUR\\b")
  private val PERCENT = Regex("([+-]?\\d{1,3}(?:[.,]\\d+)?)\\s?%")
  private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
  private val TOLERANCE = BigDecimal("0.05")
  private const val ASSET_TOTAL = "aktivate turuväärtus kokku"
  private const val ISIN_LENGTH = 12
  private const val RADIX = 36
  private val HUNDRED = BigDecimal(100)

  fun parse(pdf: ByteArray): TulevaAllocationReport =
    Loader.loadPDF(pdf).use { parse(PDFTextStripper().apply { sortByPosition = true }.getText(it)) }

  fun parse(text: String): TulevaAllocationReport {
    val lines = text.lines()
    val totalIndex = lines.indexOfFirst { it.lowercase().contains(ASSET_TOTAL) }
    require(totalIndex >= 0) { "Tuleva report has no asset total line" }
    val total =
      requireNotNull(percent(lines[totalIndex]) ?: lines.getOrNull(totalIndex + 1)?.let(::percent)) {
        "Tuleva report asset total line has no percentage: ${lines[totalIndex]}"
      }
    val body = lines.take(totalIndex)
    val rows = body.mapNotNull(::fundRow)
    val other = body.filter(::isOtherAsset).mapNotNull(::percent).sumOf { it }
    validate(rows, other, total)
    return TulevaAllocationReport(asOfDate = date(text), reportedTotal = total, rows = rows)
  }

  private fun date(text: String): LocalDate {
    val raw = requireNotNull(DATE.find(text)?.groupValues?.get(1)) { "Tuleva report has no report date" }
    return runCatching { LocalDate.parse(raw, DATE_FORMAT) }
      .getOrElse { throw IllegalArgumentException("Tuleva report date $raw is not a valid date", it) }
  }

  private fun fundRow(line: String): TulevaAllocationRow? {
    val match = FUND_ROW.find(line) ?: return null
    val raw = match.groupValues[1]
    val weight = requireNotNull(percent(line.substring(match.range.last + 1))) { "Tuleva fund row $raw has no weight" }
    val isin = normalize(raw)
    return TulevaAllocationRow(
      isin = isin,
      name = line.substring(0, match.range.first).trim(),
      weight = weight,
      repairedFrom = raw.takeIf { it != isin },
    )
  }

  private fun normalize(raw: String): String {
    if (raw.length == ISIN_LENGTH) {
      require(hasValidCheckDigit(raw)) { "Tuleva fund ISIN $raw has an invalid check digit" }
      return raw
    }
    val matches = TULEVA_PROXIES.keys.filter { it.startsWith(raw) }
    require(matches.size == 1) { "Tuleva fund ISIN $raw is truncated and matches $matches" }
    return matches.single()
  }

  private fun hasValidCheckDigit(isin: String): Boolean =
    isin
      .map { it.digitToInt(RADIX) }
      .joinToString("")
      .reversed()
      .mapIndexed { index, char -> luhnDigit(char.digitToInt(), index) }
      .sum() % 10 == 0

  private fun luhnDigit(
    digit: Int,
    index: Int,
  ): Int {
    if (index % 2 == 0) return digit
    val doubled = digit * 2
    return doubled / 10 + doubled % 10
  }

  private fun isOtherAsset(line: String): Boolean {
    val lower = line.lowercase()
    if (lower.contains("kokku") || lower.contains("total")) return false
    return line.contains(" EE EUR ") || lower.startsWith("muud nõuded")
  }

  private fun percent(text: String): BigDecimal? =
    PERCENT
    .find(text)
    ?.groupValues
    ?.get(1)
    ?.replace(',', '.')
    ?.let(::BigDecimal)

  private fun validate(
    rows: List<TulevaAllocationRow>,
    other: BigDecimal,
    total: BigDecimal,
  ) {
    require(rows.isNotEmpty()) { "Tuleva report has no fund rows" }
    val duplicates = rows.groupBy { it.isin }.filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "Duplicate Tuleva fund ISINs $duplicates" }
    rows.forEach { require(it.weight in BigDecimal.ZERO..HUNDRED) { "Tuleva fund ${it.isin} weight ${it.weight} is out of range" } }
    val sum = other + rows.sumOf { it.weight }
    require((sum - total).abs() <= TOLERANCE) { "Tuleva report rows sum to $sum but the asset total is $total" }
  }
}
