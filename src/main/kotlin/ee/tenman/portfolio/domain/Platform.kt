package ee.tenman.portfolio.domain

enum class Platform(
  val displayName: String,
) {
  AVIVA("Aviva"),
  BINANCE("Binance"),
  COINBASE("Coinbase"),
  IBKR("IBKR"),
  LHV("LHV"),
  LIGHTYEAR("Lightyear"),
  SWEDBANK("Swedbank"),
  TRADING212("Trading 212"),
  UNKNOWN("Unknown"),
  ;

  companion object {
    fun fromStringOrNull(value: String): Platform? = runCatching { valueOf(value.uppercase()) }.getOrNull()

    fun parseList(values: List<String>?): List<Platform>? {
      if (values.isNullOrEmpty()) return null
      val parsed = values.mapNotNull { fromStringOrNull(it) }.sortedBy { it.name }
      return parsed.ifEmpty { null }
    }
  }
}
