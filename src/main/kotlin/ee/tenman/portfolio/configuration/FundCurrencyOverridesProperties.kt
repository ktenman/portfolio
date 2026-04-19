package ee.tenman.portfolio.configuration

import ee.tenman.portfolio.domain.Currency
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "fund-currency")
data class FundCurrencyOverridesProperties(
  val overrides: Map<String, String> = emptyMap(),
) {
  fun forSymbol(symbol: String): Currency? = overrides[symbol]?.let(Currency::fromCodeOrNull)

  @PostConstruct
  fun validate() {
    val invalid = overrides.filterValues { Currency.fromCodeOrNull(it) == null }
    check(invalid.isEmpty()) {
      "fund-currency.overrides contains unknown codes: $invalid (allowed: ${Currency.entries.joinToString(", ")})"
    }
  }
}
