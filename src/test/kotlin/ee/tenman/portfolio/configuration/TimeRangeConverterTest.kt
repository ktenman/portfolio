package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.TimeRange
import org.junit.jupiter.api.Test

class TimeRangeConverterTest {
  private val converter = TimeRangeConverter()

  @Test
  fun `should convert a range code into its enum constant`() {
    expect(converter.convert("6M")).toEqual(TimeRange.SIX_MONTHS)
  }

  @Test
  fun `should throw when the range code is unknown`() {
    expect { converter.convert("42Y") }.toThrow<IllegalArgumentException>()
  }
}
