package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.domain.VanguardCountryUpdate
import ee.tenman.portfolio.domain.VanguardIndustryUpdate
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.dto.IdentityPair
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.testing.fixture.answerPairs
import ee.tenman.portfolio.vanguard.VanguardFundSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class EtfHoldingServiceTest {
  private val etfHoldingPersistenceService = mockk<EtfHoldingPersistenceService>()
  private val holdingIdentityService = mockk<HoldingIdentityService>(relaxed = true)
  private val minioService = mockk<MinioService>(relaxed = true)
  private val imageDownloadService = mockk<ImageDownloadService>()
  private val imageProcessingService = mockk<ImageProcessingService>()
  private lateinit var service: EtfHoldingService
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setup() {
    holdingIdentityService.answerPairs { null }
    every { etfHoldingPersistenceService.findByTicker(any()) } returns emptyList()
    every { etfHoldingPersistenceService.findByNameBlockKey(any()) } returns emptyList()
    service =
      EtfHoldingService(
        etfHoldingPersistenceService,
        holdingIdentityService,
        minioService,
        imageDownloadService,
        imageProcessingService,
      )
  }

  @Test
  fun `should download and upload logo via saveHoldings`() {
    val holdingUuid = UUID.randomUUID()
    val holding = createHolding(1L, "NVDA", "NVIDIA Corp", uuid = holdingUuid)
    val imageData = "image-bytes".toByteArray()
    val processedImage = "processed-bytes".toByteArray()
    val holdingData =
      HoldingData(
        name = "NVIDIA Corp",
        ticker = "NVDA",
        sector = null,
        weight = BigDecimal.TEN,
        rank = 1,
        logoUrl = "https://lightyear.com/logo.png",
      )
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(holdingData), emptyMap()) } returns
      mapOf("NVIDIA Corp" to holding)
    every { imageDownloadService.download("https://lightyear.com/logo.png") } returns imageData
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
    every { etfHoldingPersistenceService.saveHolding(holding) } returns holding

    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    expect(holding.logoSource).toEqual(LogoSource.LIGHTYEAR)
    verify { minioService.uploadLogo(holdingUuid, processedImage) }
    verify { etfHoldingPersistenceService.saveHolding(holding) }
  }

  @Test
  fun `should skip logo download when logoUrl is null via saveHoldings`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    val holdingData = createHoldingData("Apple Inc", "AAPL", null)
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(holdingData), emptyMap()) } returns
      mapOf("Apple Inc" to holding)

    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    verify(exactly = 0) { imageDownloadService.download(any()) }
    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
  }

  @Test
  fun `should skip logo download when logo source is already LIGHTYEAR`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc", logoSource = LogoSource.LIGHTYEAR)
    val holdingData =
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = null,
        weight = BigDecimal.TEN,
        rank = 1,
        logoUrl = "https://lightyear.com/logo.png",
      )
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(holdingData), emptyMap()) } returns
      mapOf("Apple Inc" to holding)

    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    verify(exactly = 0) { imageDownloadService.download(any()) }
  }

  @Test
  fun `should upgrade BING logo to LIGHTYEAR when lightyear url provided`() {
    val holdingUuid = UUID.randomUUID()
    val holding = createHolding(1L, "AAPL", "Apple Inc", logoSource = LogoSource.BING, uuid = holdingUuid)
    val imageData = "image-bytes".toByteArray()
    val processedImage = "processed-bytes".toByteArray()
    val holdingData =
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = null,
        weight = BigDecimal.TEN,
        rank = 1,
        logoUrl = "https://lightyear.com/logo.png",
      )
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(holdingData), emptyMap()) } returns
      mapOf("Apple Inc" to holding)
    every { imageDownloadService.download("https://lightyear.com/logo.png") } returns imageData
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
    every { etfHoldingPersistenceService.saveHolding(holding) } returns holding

    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    expect(holding.logoSource).toEqual(LogoSource.LIGHTYEAR)
    verify { minioService.uploadLogo(holdingUuid, processedImage) }
    verify { etfHoldingPersistenceService.saveHolding(holding) }
  }

  @Test
  fun `should handle download failure gracefully`() {
    val holding = createHolding(1L, "TSLA", "Tesla Inc")
    val holdingData =
      HoldingData(
        name = "Tesla Inc",
        ticker = "TSLA",
        sector = null,
        weight = BigDecimal.TEN,
        rank = 1,
        logoUrl = "https://lightyear.com/tesla.png",
      )
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(holdingData), emptyMap()) } returns
      mapOf("Tesla Inc" to holding)
    every { imageDownloadService.download("https://lightyear.com/tesla.png") } throws RuntimeException("Network error")

    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    expect(holding.logoSource).toEqual(null)
    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
  }

  @Test
  fun `should resolve an exact industry holding to its existing uuid`() {
    val holding = createHolding(17L, "HLN", "Haleon PLC")
    val data = createHoldingData("HALEON PLC", "HLN", null).copy(industry = GicsIndustry.PHARMACEUTICALS)
    every { etfHoldingPersistenceService.findByTicker("HLN") } returns listOf(holding)

    expect(service.resolveVanguardUpdates(VanguardFundSnapshot(testDate, listOf(data))).industries).toEqual(
      listOf(VanguardIndustryUpdate(holding.uuid, GicsIndustry.PHARMACEUTICALS, testDate)),
    )
  }

  @Test
  fun `should resolve an industry alias only after confirming company identity`() {
    val holding = createHolding(29L, "LONN", "Lonza")
    val data = createHoldingData("Lonza Group AG", "LONN", null).copy(industry = GicsIndustry.LIFE_SCIENCES_TOOLS_AND_SERVICES)
    every { etfHoldingPersistenceService.findByTicker("LONN") } returns listOf(holding)
    holdingIdentityService.answerPairs(mapOf(IdentityPair("Lonza", "Lonza Group AG", "LONN", null, null, "LONN") to true)::get)

    expect(service.resolveVanguardUpdates(VanguardFundSnapshot(testDate, listOf(data))).industries).toEqual(
      listOf(VanguardIndustryUpdate(holding.uuid, GicsIndustry.LIFE_SCIENCES_TOOLS_AND_SERVICES, testDate)),
    )
  }

  @Test
  fun `cannot resolve an industry from a ticker without matching company identity`() {
    val holding = createHolding(31L, "SU", "Suncor")
    val data = createHoldingData("Schneider Electric SE", "SU", null).copy(industry = GicsIndustry.ELECTRICAL_EQUIPMENT)
    every { etfHoldingPersistenceService.findByTicker("SU") } returns listOf(holding)
    holdingIdentityService.answerPairs(mapOf(IdentityPair("Suncor", "Schneider Electric SE", "SU") to false)::get)

    expect(service.resolveVanguardUpdates(VanguardFundSnapshot(testDate, listOf(data))).industries).toEqual(emptyList())
  }

  @Test
  fun `cannot resolve an industry when multiple company identities match`() {
    val data = createHoldingData("Merck Inc", "MRK", null).copy(industry = GicsIndustry.PHARMACEUTICALS)
    val holdings = listOf(createHolding(43L, "MRK", "Merck & Co"), createHolding(47L, "MRK", "Merck KGaA"))
    every { etfHoldingPersistenceService.findByTicker("MRK") } returns holdings
    holdingIdentityService.answerPairs { it.candidateName == "Merck Inc" && it.ticker == "MRK" }

    expect(service.resolveVanguardUpdates(VanguardFundSnapshot(testDate, listOf(data))).industries).toEqual(emptyList())
  }

  @Test
  fun `should prefer an exact name over other companies sharing the ticker`() {
    val expected = createHolding(53L, "CFR", "Cie Financiere Richemont SA")
    val other = createHolding(59L, "CFR", "Cullen Frost")
    val data = createHoldingData(expected.name, "CFR", null).copy(industry = GicsIndustry.TEXTILES_APPAREL_AND_LUXURY_GOODS)
    every { etfHoldingPersistenceService.findByTicker("CFR") } returns listOf(other, expected)
    holdingIdentityService.answerPairs { true }

    expect(service.resolveVanguardUpdates(VanguardFundSnapshot(testDate, listOf(data))).industries).toEqual(
      listOf(VanguardIndustryUpdate(expected.uuid, GicsIndustry.TEXTILES_APPAREL_AND_LUXURY_GOODS, testDate)),
    )
  }

  @Test
  fun `cannot resolve an industry when Vanguard supplies no classification`() {
    val data = createHoldingData("Tundmatu Ühistu OÜ", null, null)

    expect(service.resolveVanguardUpdates(VanguardFundSnapshot(testDate, listOf(data))).industries).toEqual(emptyList())
  }

  @Test
  fun `should resolve country and industry through the same confirmed company identity once`() {
    val holding = createHolding(67L, "HLN", "Haleon")
    val data = createHoldingData("Haleon PLC", "HLN", null).copy(industry = GicsIndustry.PHARMACEUTICALS)
    every { etfHoldingPersistenceService.findByTicker("HLN") } returns listOf(holding)
    holdingIdentityService.answerPairs(mapOf(IdentityPair("Haleon", "Haleon PLC", "HLN", null, null, "HLN") to true)::get)
    val updates = service.resolveVanguardUpdates(VanguardFundSnapshot(testDate, listOf(data), mapOf(data.name to setOf("GB"))))
    expect(updates.countries).toEqual(listOf(VanguardCountryUpdate(holding.uuid, "GB", testDate)))
    expect(updates.industries.single().holdingUuid).toEqual(holding.uuid)
    verify(exactly = 1) { holdingIdentityService.resolveAll(listOf(IdentityPair("Haleon", "Haleon PLC", "HLN", null, null, "HLN"))) }
  }

  @Test
  fun `should preserve all conflicting country observations even without an industry`() {
    val holding = createHolding(71L, "SHOP", "Shopify Inc")
    val data = createHoldingData(holding.name, "SHOP", null)
    every { etfHoldingPersistenceService.findByTicker("SHOP") } returns listOf(holding)
    val updates = service.resolveVanguardUpdates(VanguardFundSnapshot(testDate, listOf(data), mapOf(data.name to setOf("CA", "US"))))
    expect(updates.industries).toEqual(emptyList())
    expect(
      updates.countries.toSet(),
    ).toEqual(setOf(VanguardCountryUpdate(holding.uuid, "CA", testDate), VanguardCountryUpdate(holding.uuid, "US", testDate)))
  }

  @Test
  fun `cannot resolve a country by a shared ticker with an unconfirmed identity`() {
    val holding = createHolding(73L, "SU", "Suncor")
    val data = createHoldingData("Schneider Electric SE", "SU", null)
    every { etfHoldingPersistenceService.findByTicker("SU") } returns listOf(holding)
    holdingIdentityService.answerPairs(mapOf(IdentityPair("Suncor", data.name, "SU") to false)::get)
    val updates = service.resolveVanguardUpdates(VanguardFundSnapshot(testDate, listOf(data), mapOf(data.name to setOf("FR"))))
    expect(updates.countries).toEqual(emptyList())
  }

  @Test
  fun `should ask about every candidate pair of a snapshot in one batch`() {
    val first = createHoldingData("Õunake Grupp", "OUN", null)
    val second = createHoldingData("Škoda Auto Holding", "SKO", null)
    every { etfHoldingPersistenceService.findByTicker("OUN") } returns listOf(createHolding(3L, "OUN", "Õunake"))
    every { etfHoldingPersistenceService.findByTicker("SKO") } returns listOf(createHolding(5L, "SKO", "Škoda Auto"))
    every { etfHoldingPersistenceService.saveHoldings(any(), any(), any(), any()) } returns emptyMap()
    service.saveHoldings("VWCE", testDate, listOf(first, second))
    verify(exactly = 1) { holdingIdentityService.resolveAll(any()) }
  }

  @Test
  fun `should hint the lower id when two candidates are both confirmed`() {
    val hints = slot<Map<Int, Long>>()
    val data = createHoldingData("Žalgiris Grupė AB", "ZAL", null)
    every { etfHoldingPersistenceService.findByTicker("ZAL") } returns
      listOf(createHolding(19L, "ZAL", "Žalgiris Grupė Holding"), createHolding(7L, "ZAL", "Žalgiris Grupė"))
    holdingIdentityService.answerPairs { it.candidateName == data.name && it.ticker == "ZAL" }
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(data), capture(hints)) } returns emptyMap()
    service.saveHoldings("VWCE", testDate, listOf(data))
    expect(hints.captured).toEqual(mapOf(0 to 7L))
  }

  @Test
  fun `should hint the first confirmed candidate at the position of its holding`() {
    val hints = slot<Map<Int, Long>>()
    val unmatched = createHoldingData("Tundmatu Ühistu", null, null)
    val matched = createHoldingData("Žalgiris Bankas AB", "ZAL", null)
    val candidates =
      listOf(
        createHolding(11L, "ZAL", "Žalgiris Energija"),
        createHolding(13L, "ZAL", "Žalgiris Bankas"),
        createHolding(17L, "ZAL", "Žalgiris"),
      )
    every { etfHoldingPersistenceService.findByTicker("ZAL") } returns candidates
    holdingIdentityService.answerPairs(
      mapOf(
        IdentityPair("Žalgiris Energija", matched.name, "ZAL", null, null, "ZAL") to false,
        IdentityPair("Žalgiris Bankas", matched.name, "ZAL", null, null, "ZAL") to true,
        IdentityPair("Žalgiris", matched.name, "ZAL", null, null, "ZAL") to true,
      )::get,
    )
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(unmatched, matched), capture(hints)) } returns emptyMap()
    service.saveHoldings("VWCE", testDate, listOf(unmatched, matched))
    expect(hints.captured).toEqual(mapOf(1 to 13L))
  }

  @Test
  fun `should pass both countries to the identity check`() {
    val pairs = slot<List<IdentityPair>>()
    val data = createHoldingData("MERCK", "MRK", null).copy(countryCode = "DE")
    every { etfHoldingPersistenceService.findByTicker("MRK") } returns
      listOf(createHolding(8L, "MRK", "Merck & Co.").apply { countryCode = "US" })
    every { holdingIdentityService.resolveAll(capture(pairs)) } returns emptyMap()
    every { etfHoldingPersistenceService.saveHoldings(any(), any(), any(), any()) } returns emptyMap()
    service.saveHoldings("SAWD", testDate, listOf(data))
    expect(pairs.captured).toEqual(listOf(IdentityPair("Merck & Co.", "MERCK", "MRK", "US", "DE", "MRK")))
  }

  private fun createHolding(
    id: Long,
    ticker: String?,
    name: String,
    logoSource: LogoSource? = null,
    uuid: UUID = UUID.randomUUID(),
  ): EtfHolding =
    EtfHolding(ticker = ticker, name = name).apply {
      this.id = id
      this.logoSource = logoSource
      this.uuid = uuid
    }

  private fun createHoldingData(
    name: String,
    ticker: String?,
    logoUrl: String?,
  ): HoldingData =
    HoldingData(
      name = name,
      ticker = ticker,
      sector = null,
      weight = BigDecimal.TEN,
      rank = 1,
      logoUrl = logoUrl,
    )
}
