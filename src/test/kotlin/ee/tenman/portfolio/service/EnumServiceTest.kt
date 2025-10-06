package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.*
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
    val result = enumService.getAllEnums()

    expect(result.keys).toContain("platforms", "providers", "transactionTypes", "categories", "currencies")

    val platforms = result["platforms"]!!
    expect(platforms).toEqual(platforms.sorted())
    expect(platforms).toHaveSize(Platform.entries.size)

    val providers = result["providers"]!!
    expect(providers).toEqual(providers.sorted())
    expect(providers).toHaveSize(ProviderName.entries.size)

    expect(result["transactionTypes"]).notToEqualNull().toContainExactly("BUY", "SELL")

    val categories = result["categories"]!!
    expect(categories).toEqual(categories.sorted())
    expect(categories).toHaveSize(InstrumentCategory.entries.size)

    val currencies = result["currencies"]!!
    expect(currencies).toEqual(currencies.sorted())
    expect(currencies).toHaveSize(Currency.entries.size)
  }

  @Test
  fun `should return expected enum values when retrieving enums`() {
    val result = enumService.getAllEnums()

    expect(result["platforms"])
      .notToEqualNull()
      .toContainExactly("AVIVA", "BINANCE", "COINBASE", "LHV", "LIGHTYEAR", "SWEDBANK", "TRADING212", "UNKNOWN")
    expect(result["providers"]).notToEqualNull().toContainExactly("ALPHA_VANTAGE", "BINANCE", "FT")
    expect(result["transactionTypes"]).notToEqualNull().toContainExactly("BUY", "SELL")
    expect(result["categories"]).notToEqualNull().toContainExactly("CRYPTO", "ETF")
    expect(result["currencies"]).notToEqualNull().toContainExactly("EUR")
  }
}
