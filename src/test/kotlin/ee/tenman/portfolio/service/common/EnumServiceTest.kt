package ee.tenman.portfolio.service.common

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Currency
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.Platform
import ee.tenman.portfolio.domain.PlatformDto
import ee.tenman.portfolio.domain.ProviderName
import org.junit.jupiter.api.Test

class EnumServiceTest {
  @Test
  fun `should expose every enum constant sorted by name`() {
    val response = EnumService().getAllEnums()

    expect(response.platforms.map { it.name }).toEqual(Platform.entries.map { it.name }.sorted())
    expect(response.providers).toEqual(ProviderName.entries.map { it.name }.sorted())
    expect(response.categories).toEqual(InstrumentCategory.entries.map { it.name }.sorted())
    expect(response.currencies).toEqual(Currency.entries.map { it.name }.sorted())
  }

  @Test
  fun `should carry a display name that differs from the constant name`() {
    val response = EnumService().getAllEnums()

    expect(response.platforms).toContain(PlatformDto(name = "TRADING212", displayName = "Trading 212"))
  }

  @Test
  fun `should keep transaction types in buy before sell order`() {
    val response = EnumService().getAllEnums()

    expect(response.transactionTypes).toContainExactly("BUY", "SELL")
  }
}
