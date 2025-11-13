package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EnumServiceTest {
  private lateinit var enumService: EnumService

  @BeforeEach
  fun setUp() {
    enumService = EnumService()
  }

  @Test
  fun `should return all enum values in sorted order when retrieving enums`() {
    val response = enumService.getAllEnums()

    expect(response.platforms)
      .toEqual(response.platforms.sorted())
      .toHaveSize(Platform.entries.size)

    expect(response.providers)
      .toEqual(response.providers.sorted())
      .toHaveSize(ProviderName.entries.size)

    expect(response.transactionTypes).toContainExactly("BUY", "SELL")

    expect(response.categories)
      .toEqual(response.categories.sorted())
      .toHaveSize(InstrumentCategory.entries.size)

    expect(response.currencies)
      .toEqual(response.currencies.sorted())
      .toHaveSize(Currency.entries.size)
  }

  @Test
  fun `should return expected enum values when retrieving enums`() {
    val response = enumService.getAllEnums()

    expect(response.platforms)
      .toContainExactly("AVIVA", "BINANCE", "COINBASE", "IBKR", "LHV", "LIGHTYEAR", "SWEDBANK", "TRADING212", "UNKNOWN")

    expect(response.providers).toContainExactly("ALPHA_VANTAGE", "BINANCE", "FT", "LIGHTYEAR", "TRADING212")

    expect(response.transactionTypes).toContainExactly("BUY", "SELL")

    expect(response.categories).toContainExactly("CRYPTO", "ETF")

    expect(response.currencies).toContainExactly("EUR")
  }
}
