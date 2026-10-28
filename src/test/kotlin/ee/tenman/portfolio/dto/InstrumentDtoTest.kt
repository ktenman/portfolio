package ee.tenman.portfolio.dto

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.model.InstrumentSnapshot
import org.junit.jupiter.api.Test

class InstrumentDtoTest {
  @Test
  fun `leaves the logo uuid empty for an instrument without a logo source`() {
    expect(InstrumentDto.fromEntity(instrument(logoSource = null)).logoUuid).toEqual(null)
  }

  @Test
  fun `exposes the instrument uuid as the logo uuid once a logo source is recorded`() {
    val instrument = instrument(logoSource = LogoSource.LIGHTYEAR)
    expect(InstrumentDto.fromEntity(instrument).logoUuid).toEqual(instrument.uuid)
  }

  @Test
  fun `carries the logo uuid through a snapshot`() {
    val instrument = instrument(logoSource = LogoSource.NVSTLY_ICONS)
    expect(InstrumentDto.fromSnapshot(InstrumentSnapshot(instrument = instrument)).logoUuid).toEqual(instrument.uuid)
  }

  private fun instrument(logoSource: LogoSource?) =
    Instrument(
      symbol = "VWCE:GER:EUR",
      name = "Vanguard FTSE All-World",
      category = "ETF",
      baseCurrency = "EUR",
      logoSource = logoSource,
    )
}
