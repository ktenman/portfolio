package ee.tenman.portfolio.trading212

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trading212Instrument(
  val ticker: String,
  val type: String,
  val isin: String?,
  val currencyCode: String?,
  val name: String?,
  val shortName: String?,
)
