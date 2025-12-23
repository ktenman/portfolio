package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class LightyearScrapingPropertiesTest {
  @Test
  fun `getAllInstruments should combine etfs and additional instruments`() {
    val properties =
      LightyearScrapingProperties(
      etfs =
        listOf(
        LightyearScrapingProperties.EtfConfig(symbol = "VUAA", uuid = "uuid-1"),
        LightyearScrapingProperties.EtfConfig(symbol = "VWCE", uuid = "uuid-2"),
      ),
        additionalInstruments =
          listOf(
        LightyearScrapingProperties.EtfConfig(symbol = "WTAI:MIL:EUR", uuid = "uuid-3"),
      ),
          )

    val result = properties.getAllInstruments()

    expect(result.keys).toContainExactly("VUAA", "VWCE", "WTAI:MIL:EUR")
    expect(result["VUAA"]).toEqual("uuid-1")
    expect(result["VWCE"]).toEqual("uuid-2")
    expect(result["WTAI:MIL:EUR"]).toEqual("uuid-3")
  }

  @Test
  fun `getAllInstruments should filter out instruments with blank uuid`() {
    val properties =
      LightyearScrapingProperties(
      etfs =
        listOf(
        LightyearScrapingProperties.EtfConfig(symbol = "VUAA", uuid = "uuid-1"),
        LightyearScrapingProperties.EtfConfig(symbol = "NO_UUID", uuid = ""),
      ),
        )

    val result = properties.getAllInstruments()

    expect(result.keys).toContainExactly("VUAA")
  }

  @Test
  fun `findUuidBySymbol should return exact match`() {
    val properties =
      LightyearScrapingProperties(
      etfs =
        listOf(
        LightyearScrapingProperties.EtfConfig(symbol = "VUAA", uuid = "uuid-1"),
      ),
        )

    val result = properties.findUuidBySymbol("VUAA")

    expect(result).toEqual("uuid-1")
  }

  @Test
  fun `findUuidBySymbol should return null for unknown symbol`() {
    val properties =
      LightyearScrapingProperties(
      etfs =
        listOf(
        LightyearScrapingProperties.EtfConfig(symbol = "VUAA", uuid = "uuid-1"),
      ),
        )

    val result = properties.findUuidBySymbol("UNKNOWN")

    expect(result).toEqual(null)
  }

  @Test
  fun `findUuidBySymbol should match by prefix when full symbol contains colon`() {
    val properties =
      LightyearScrapingProperties(
      additionalInstruments =
        listOf(
        LightyearScrapingProperties.EtfConfig(symbol = "WTAI:MIL:EUR", uuid = "uuid-3"),
      ),
        )

    val result = properties.findUuidBySymbol("WTAI")

    expect(result).toEqual("uuid-3")
  }

  @Test
  fun `findUuidBySymbol should prefer exact match over prefix match`() {
    val properties =
      LightyearScrapingProperties(
      etfs =
        listOf(
        LightyearScrapingProperties.EtfConfig(symbol = "VUAA", uuid = "exact-uuid"),
        LightyearScrapingProperties.EtfConfig(symbol = "VUAA:GER:EUR", uuid = "full-uuid"),
      ),
        )

    val result = properties.findUuidBySymbol("VUAA")

    expect(result).toEqual("exact-uuid")
  }

  @Test
  fun `getAllInstruments should return empty map when no instruments configured`() {
    val properties = LightyearScrapingProperties()

    val result = properties.getAllInstruments()

    expect(result).toBeEmpty()
  }
}
