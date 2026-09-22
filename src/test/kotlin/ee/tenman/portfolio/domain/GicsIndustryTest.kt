package ee.tenman.portfolio.domain

import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.notToThrow
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GicsIndustryTest {
  @Test
  fun `should define exactly 74 industries`() {
    expect(GicsIndustry.entries).toHaveSize(74)
  }

  @Test
  fun `should define exactly 11 sectors`() {
    expect(GicsSector.entries).toHaveSize(11)
  }

  @Test
  fun `should give every industry a unique six digit code`() {
    val codes = GicsIndustry.entries.map { it.code }
    expect(codes.toSet()).toHaveSize(74)
    expect(codes.all { it in 100000..999999 }).toEqual(true)
  }

  @Test
  fun `should derive the sector from the first two digits of the code`() {
    expect(GicsIndustry.AEROSPACE_AND_DEFENSE.sector).toEqual(GicsSector.INDUSTRIALS)
    expect(GicsIndustry.BANKS.sector).toEqual(GicsSector.FINANCIALS)
    expect(GicsIndustry.SPECIALIZED_REITS.sector).toEqual(GicsSector.REAL_ESTATE)
  }

  @Test
  fun `should derive the industry group from the first four digits of the code`() {
    expect(GicsIndustry.AEROSPACE_AND_DEFENSE.groupCode).toEqual(2010)
    expect(GicsIndustry.ELECTRICAL_EQUIPMENT.groupCode).toEqual(2010)
    expect(GicsIndustry.PROFESSIONAL_SERVICES.groupCode).toEqual(2020)
  }

  @Test
  fun `should map every industry to a defined sector`() {
    expect { GicsIndustry.entries.forEach { it.sector } }.notToThrow()
  }

  @Test
  fun `should resolve an industry from its code`() {
    expect(GicsIndustry.fromCode(453010)).toEqual(GicsIndustry.SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT)
  }

  @Test
  fun `cannot resolve an industry from an unknown code`() {
    expect(GicsIndustry.fromCode(999999)).toEqual(null)
  }

  @Test
  fun `should list every industry as code and name in the prompt catalogue`() {
    val lines = GicsIndustry.promptCatalogue().lines()
    expect(lines).toHaveSize(74)
    expect(lines).toContain("201010 Aerospace & Defense")
  }

  @Test
  fun `cannot map any industry to cryptocurrency`() {
    expect(GicsIndustry.entries.map { it.industrySector }).notToContain(IndustrySector.CRYPTOCURRENCY)
  }

  @ParameterizedTest
  @CsvSource(
    "GROUND_TRANSPORTATION, MOBILITY",
    "AIR_FREIGHT_AND_LOGISTICS, MOBILITY",
    "AUTOMOBILES, MOBILITY",
    "COMMUNICATIONS_EQUIPMENT, COMMUNICATION",
    "IT_SERVICES, BUSINESS_SERVICES",
    "SPECIALIZED_REITS, BUSINESS_SERVICES",
    "BROADLINE_RETAIL, CONSUMER_ESSENTIALS",
    "SEMICONDUCTORS_AND_SEMICONDUCTOR_EQUIPMENT, SEMICONDUCTORS",
    "INTERACTIVE_MEDIA_AND_SERVICES, SOFTWARE_CLOUD_SERVICES",
    "METALS_AND_MINING, INDUSTRIALS",
    "INDEPENDENT_POWER_AND_RENEWABLE_ELECTRICITY_PRODUCERS, ENERGY",
    "REAL_ESTATE_MANAGEMENT_AND_DEVELOPMENT, FINANCE",
  )
  fun `should map an industry onto its portfolio sector`(
    industry: GicsIndustry,
    sector: IndustrySector,
  ) {
    expect(industry.industrySector).toEqual(sector)
  }
}
