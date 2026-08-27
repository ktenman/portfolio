package ee.tenman.portfolio.domain

import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate

data class DailyPricePoint(
  val instrumentId: Long,
  val entryDate: LocalDate,
  val closePrice: BigDecimal,
)

object HoldingBlockKey {
  private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

  fun of(name: String): String =
    name
      .map { it.lowercaseChar() }
      .joinToString("")
      .split(NON_ALPHANUMERIC)
      .firstOrNull { it.isNotEmpty() }
      ?: ""
}

object HoldingNameSimilarity {
  private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
  private val COMBINING_MARKS = Regex("\\p{Mn}+")
  private const val MIN_PREFIX_LENGTH = 3
  private const val REQUIRED_MATCHES = 2
  private val LEGAL_FORMS =
    (
      "co company corp corporation inc incorporated ltd limited plc llc lp sa ag nv ab as oyj spa se " +
        "gmbh kgaa pjsc jsc psc bhd tbk pt pte adr gdr reit class the of and"
    ).split(" ").toSet()

  fun mayBeSameCompany(
    first: String,
    second: String,
  ): Boolean {
    val left = tokenize(first)
    val right = tokenize(second)
    if (left.size < REQUIRED_MATCHES || right.size < REQUIRED_MATCHES) return true
    return left.count { token -> right.any { alike(token, it) } } >= REQUIRED_MATCHES
  }

  private fun tokenize(name: String): List<String> =
    Normalizer
      .normalize(name, Normalizer.Form.NFD)
      .replace(COMBINING_MARKS, "")
      .map { it.lowercaseChar() }
      .joinToString("")
      .split(NON_ALPHANUMERIC)
      .filter { it.length > 1 && it !in LEGAL_FORMS }

  private fun alike(
    first: String,
    second: String,
  ): Boolean {
    if (first == second) return true
    if (minOf(first.length, second.length) < MIN_PREFIX_LENGTH) return false
    return first.startsWith(second) || second.startsWith(first)
  }
}
