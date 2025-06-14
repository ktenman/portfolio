package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.ProviderName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EnumServiceTest {
  private lateinit var enumService: EnumService

  @BeforeEach
  fun setUp() {
    enumService = EnumService()
  }

  @Test
  fun `should return all enum values in sorted order`() {
    val result = enumService.getAllEnums()

    assertThat(result).containsKeys("platforms", "providers", "transactionTypes", "categories", "currencies")
    assertThat(result["platforms"]).isSortedAccordingTo(String::compareTo)
    assertThat(result["platforms"]).hasSize(Platform.entries.size)
    assertThat(result["providers"]).isSortedAccordingTo(String::compareTo)
    assertThat(result["providers"]).hasSize(ProviderName.entries.size)
    assertThat(result["transactionTypes"]).containsExactly("BUY", "SELL")
    assertThat(result["categories"]).isSortedAccordingTo(String::compareTo)
    assertThat(result["categories"]).hasSize(InstrumentCategory.entries.size)
    assertThat(result["currencies"]).isSortedAccordingTo(String::compareTo)
    assertThat(result["currencies"]).hasSize(Currency.entries.size)
  }

  @Test
  fun `should return expected enum values`() {
    val result = enumService.getAllEnums()

    assertThat(
      result["platforms"],
    ).containsExactly("AVIVA", "BINANCE", "COINBASE", "LHV", "LIGHTYEAR", "SWEDBANK", "TRADING212", "UNKNOWN")
    assertThat(result["providers"]).containsExactly("ALPHA_VANTAGE", "BINANCE", "FT")
    assertThat(result["transactionTypes"]).containsExactly("BUY", "SELL")
    assertThat(result["categories"]).containsExactly("CRYPTO", "ETF")
    assertThat(result["currencies"]).containsExactly("EUR")
  }
}
