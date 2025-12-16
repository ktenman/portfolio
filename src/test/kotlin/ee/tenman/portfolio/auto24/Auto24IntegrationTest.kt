package ee.tenman.portfolio.auto24

import ch.tutteli.atrium.api.fluent.en_GB.notToBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeAnInstanceOf
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IntegrationTest
import jakarta.annotation.Resource
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@IntegrationTest
@Tag("auto24")
class Auto24IntegrationTest {
  @Resource
  private lateinit var auto24Service: Auto24Service

  @Test
  fun `should return price range for valid vehicle`() {
    val result = auto24Service.findCarPrice("876BCH")

    expect(result).notToEqualNull().notToBeEmpty()
    expect(result).toContain("€")
    expect(result).toContain("kuni")
  }

  @Test
  fun `should return vehicle not found for invalid plate`() {
    val result = auto24Service.findCarPrice("XXXXXX")

    expect(result).toEqual("Vehicle not found")
  }

  @Test
  fun `should handle multiple requests`() {
    val plates = listOf("876BCH", "463BKH")
    val results = plates.map { auto24Service.findCarPrice(it) }

    results.forEach { result ->
      expect(result).notToEqualNull().notToBeEmpty()
      expect(result).toBeAnInstanceOf<String>()
    }
  }
}
