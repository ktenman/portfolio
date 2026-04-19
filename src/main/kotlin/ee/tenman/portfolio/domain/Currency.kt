package ee.tenman.portfolio.domain

enum class Currency {
  EUR,
  USD,
  GBP,
  CHF,
  JPY,
  CAD,
  AUD,
  SEK,
  NOK,
  DKK,
  HKD,
  SGD,
  ;

  companion object {
    fun fromCodeOrNull(code: String?): Currency? = code?.let { runCatching { valueOf(it.uppercase()) }.getOrNull() }
  }
}
