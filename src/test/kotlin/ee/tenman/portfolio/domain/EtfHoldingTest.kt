package ee.tenman.portfolio.domain

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

class EtfHoldingTest {
  @ParameterizedTest
  @EnumSource(SectorSource::class)
  fun `should replace existing sectors with the sector of a Vanguard industry`(source: SectorSource) {
    val holding =
      EtfHolding(
        name = "SoftBank Group Corp",
        industry = GicsIndustry.WIRELESS_TELECOMMUNICATION_SERVICES,
        industrySource = IndustrySource.VANGUARD,
        sector = IndustrySector.FINANCE,
        sectorSource = source,
        classifiedByModel = AiModel.GPT_5_6_LUNA,
      )

    holding.deriveSector()

    expect(holding.sector to holding.sectorSource).toEqual(IndustrySector.COMMUNICATION to SectorSource.INDUSTRY)
    expect(holding.classifiedByModel).toEqual(null)
  }

  @ParameterizedTest
  @EnumSource(SectorSource::class)
  fun `cannot accept an independent sector when the industry comes from Vanguard`(source: SectorSource) {
    val holding =
      EtfHolding(
        name = "Shopify Inc",
        industry = GicsIndustry.IT_SERVICES,
        industrySource = IndustrySource.VANGUARD,
        sector = IndustrySector.BUSINESS_SERVICES,
        sectorSource = SectorSource.INDUSTRY,
      )

    expect(holding.acceptsSectorFrom(source)).toEqual(false)
  }

  @Test
  fun `should derive a missing sector from the industry`() {
    val holding = EtfHolding(name = "Škoda Auto a.s.", industry = GicsIndustry.AUTOMOBILES)

    holding.deriveSector()

    expect(holding.sector).toEqual(IndustrySector.MOBILITY)
  }

  @Test
  fun `should mark a derived sector as sourced from the industry`() {
    val holding = EtfHolding(name = "Škoda Auto a.s.", industry = GicsIndustry.AUTOMOBILES)

    holding.deriveSector()

    expect(holding.sectorSource).toEqual(SectorSource.INDUSTRY)
  }

  @Test
  fun `should clear the classifying model when deriving a sector`() {
    val holding = EtfHolding(name = "Škoda Auto a.s.", industry = GicsIndustry.AUTOMOBILES, classifiedByModel = AiModel.GPT_6_LUNA)

    holding.deriveSector()

    expect(holding.classifiedByModel).toEqual(null)
  }

  @Test
  fun `cannot derive over an existing sector`() {
    val holding =
      EtfHolding(
        name = "Deutsche Post AG",
        industry = GicsIndustry.AIR_FREIGHT_AND_LOGISTICS,
        sector = IndustrySector.INDUSTRIALS,
        sectorSource = SectorSource.LLM,
      )

    holding.deriveSector()

    expect(holding.sector to holding.sectorSource).toEqual(IndustrySector.INDUSTRIALS to SectorSource.LLM)
  }

  @Test
  fun `cannot derive a sector without an industry`() {
    val holding = EtfHolding(name = "Tundmatu Ühistu OÜ")

    holding.deriveSector()

    expect(holding.sector to holding.sectorSource).toEqual(null to null)
  }

  @ParameterizedTest
  @EnumSource(SectorSource::class)
  fun `should accept a sector from any source when the holding has none`(source: SectorSource) {
    expect(EtfHolding(name = "Tundmatu Ühistu OÜ").acceptsSectorFrom(source)).toEqual(true)
  }

  @ParameterizedTest
  @CsvSource(
    "LLM, LIGHTYEAR, true",
    "INDUSTRY, LIGHTYEAR, true",
    ", LIGHTYEAR, true",
    "LIGHTYEAR, LIGHTYEAR, false",
    "LLM, LLM, false",
    "INDUSTRY, LLM, false",
    "LIGHTYEAR, LLM, false",
    "LLM, INDUSTRY, false",
    "LIGHTYEAR, INDUSTRY, false",
  )
  fun `should accept a replacement sector only from lightyear over a non lightyear source`(
    current: SectorSource?,
    incoming: SectorSource,
    accepted: Boolean,
  ) {
    val holding = EtfHolding(name = "Škoda Auto a.s.", sector = IndustrySector.MOBILITY, sectorSource = current)

    expect(holding.acceptsSectorFrom(incoming)).toEqual(accepted)
  }
}
