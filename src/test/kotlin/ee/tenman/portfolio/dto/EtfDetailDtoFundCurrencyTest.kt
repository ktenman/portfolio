package ee.tenman.portfolio.dto

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Currency
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EtfDetailDtoFundCurrencyTest {
  @Test
  fun `supports fundCurrency field`() {
    val dto =
      EtfDetailDto(
      instrumentId = 1,
      symbol = "VWCE",
      name = "Vanguard",
      allocation = BigDecimal.ZERO,
      ter = null,
      annualReturn = null,
      currentPrice = null,
      fundCurrency = Currency.USD,
    )
    expect(dto.fundCurrency).toEqual(Currency.USD)
  }
}
