package ee.tenman.portfolio.service

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.IndustrySector
import org.junit.jupiter.api.Test

class IndustrySectorTest {
  @Test
  fun `should return correct sector from display name`() {
    val result = IndustrySector.fromDisplayName("Semiconductors")

    expect(result).notToEqualNull().toEqual(IndustrySector.SEMICONDUCTORS)
  }

  @Test
  fun `should return correct sector from case-insensitive display name`() {
    val result = IndustrySector.fromDisplayName("software & cloud services")

    expect(result).notToEqualNull().toEqual(IndustrySector.SOFTWARE_CLOUD_SERVICES)
  }

  @Test
  fun `should return null for unknown display name`() {
    val result = IndustrySector.fromDisplayName("Unknown Sector")

    expect(result == null).toEqual(true)
  }

  @Test
  fun `should return all display names`() {
    val displayNames = IndustrySector.getAllDisplayNames()

    expect(displayNames.contains("Semiconductors")).toEqual(true)
    expect(displayNames.contains("Finance")).toEqual(true)
  }
}
