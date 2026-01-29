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
  }
}
