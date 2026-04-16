package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class Trading212ScrapingPropertiesTest {
  @Test
  fun `should return ticker for known symbol`() {
    val properties = Trading212ScrapingProperties()
    properties.symbols.add(Trading212SymbolEntry(symbol = "BNKE:PAR:EUR", ticker = "BNKEp_EQ"))

    val ticker = properties.findTickerBySymbol("BNKE:PAR:EUR")

    expect(ticker).toEqual("BNKEp_EQ")
  }

  @Test
  fun `should return null when symbol is unknown`() {
    val properties = Trading212ScrapingProperties()

    val ticker = properties.findTickerBySymbol("UNKNOWN:SYM:EUR")

    expect(ticker).toEqual(null)
  }
}
