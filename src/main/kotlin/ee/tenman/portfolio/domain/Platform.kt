package ee.tenman.portfolio.domain

enum class Platform {
  AVIVA,
  BINANCE,
  COINBASE,
  IBKR,
  LHV,
  LIGHTYEAR,
  SWEDBANK,
  TRADING212,
  UNKNOWN,
  ;

  companion object {
    fun fromStringOrNull(value: String): Platform? = runCatching { valueOf(value.uppercase()) }.getOrNull()

    fun parseFrom(platforms: List<String>?): Set<Platform>? {
      if (platforms.isNullOrEmpty()) return null
      return platforms.mapNotNull { fromStringOrNull(it) }.toSet().takeIf { it.isNotEmpty() }
    }
  }
}
