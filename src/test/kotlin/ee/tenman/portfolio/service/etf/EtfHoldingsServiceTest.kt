package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class EtfHoldingsServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val etfHoldingRepository = mockk<EtfHoldingRepository>()
  private val etfPositionRepository = mockk<EtfPositionRepository>()
  private val minioService = mockk<MinioService>()
  private val imageDownloadService = mockk<ImageDownloadService>()
  private val imageProcessingService = mockk<ImageProcessingService>()
  private lateinit var service: EtfHoldingsService
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setup() {
    service =
      EtfHoldingsService(
        instrumentRepository,
        etfHoldingRepository,
        etfPositionRepository,
        minioService,
        imageDownloadService,
        imageProcessingService,
      )
  }

  @Test
  fun `should return existing holding when found by name`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    every { etfHoldingRepository.findByNameIgnoreCase("Apple Inc") } returns Optional.of(holding)

    val result = service.findOrCreateHolding("Apple Inc", "AAPL", null)

    expect(result.id).toEqual(1L)
    expect(result.name).toEqual("Apple Inc")
    verify(exactly = 0) { etfHoldingRepository.save(any()) }
  }

  @Test
  fun `should create new holding when not found`() {
    val savedHolding = createHolding(2L, "NVDA", "NVIDIA Corp")
    every { etfHoldingRepository.findByNameIgnoreCase("NVIDIA Corp") } returns Optional.empty()
    every { etfHoldingRepository.save(any()) } returns savedHolding

    val result = service.findOrCreateHolding("NVIDIA Corp", "NVDA", "Technology")

    expect(result.id).toEqual(2L)
    verify { etfHoldingRepository.save(any()) }
  }

  @Test
  fun `should update ticker when existing holding has no ticker`() {
    val holding = createHolding(1L, null, "Apple Inc")
    every { etfHoldingRepository.findByNameIgnoreCase("Apple Inc") } returns Optional.of(holding)

    service.findOrCreateHolding("Apple Inc", "AAPL", null)

    expect(holding.ticker).toEqual("AAPL")
  }

  @Test
  fun `should not update ticker when existing holding already has ticker`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    every { etfHoldingRepository.findByNameIgnoreCase("Apple Inc") } returns Optional.of(holding)

    service.findOrCreateHolding("Apple Inc", "DIFFERENT", null)

    expect(holding.ticker).toEqual("AAPL")
  }

  @Test
  fun `should download and upload logo via saveHoldings`() {
    val etf = mockk<Instrument>()
    every { etf.id } returns 100L
    val holding = createHolding(1L, "NVDA", "NVIDIA Corp")
    val imageData = "image-bytes".toByteArray()
    val processedImage = "processed-bytes".toByteArray()
    val positionSlot = slot<ee.tenman.portfolio.domain.EtfPosition>()

    every { instrumentRepository.findBySymbol("VWCE") } returns Optional.of(etf)
    every { etfHoldingRepository.findByNameIgnoreCase("NVIDIA Corp") } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns false
    every { imageDownloadService.download("https://lightyear.com/logo.png") } returns imageData
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
    every { minioService.uploadLogo(1L, processedImage) } returns Unit
    every { etfPositionRepository.findByEtfInstrumentAndHoldingIdAndSnapshotDate(any(), any(), any()) } returns null
    every { etfPositionRepository.save(capture(positionSlot)) } answers { positionSlot.captured }

    val holdingData =
      HoldingData(
        name = "NVIDIA Corp",
        ticker = "NVDA",
        sector = null,
        weight = BigDecimal.TEN,
        rank = 1,
        logoUrl = "https://lightyear.com/logo.png",
      )
    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    expect(holding.logoFetched).toEqual(true)
    expect(holding.logoSource).toEqual(LogoSource.LIGHTYEAR)
    verify { minioService.uploadLogo(1L, processedImage) }
  }

  @Test
  fun `should skip logo download when logoUrl is null via saveHoldings`() {
    val etf = mockk<Instrument>()
    every { etf.id } returns 100L
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    val positionSlot = slot<ee.tenman.portfolio.domain.EtfPosition>()

    every { instrumentRepository.findBySymbol("VWCE") } returns Optional.of(etf)
    every { etfHoldingRepository.findByNameIgnoreCase("Apple Inc") } returns Optional.of(holding)
    every { etfPositionRepository.findByEtfInstrumentAndHoldingIdAndSnapshotDate(any(), any(), any()) } returns null
    every { etfPositionRepository.save(capture(positionSlot)) } answers { positionSlot.captured }

    val holdingData = createHoldingData("Apple Inc", "AAPL", null)
    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    verify(exactly = 0) { imageDownloadService.download(any()) }
    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
  }

  @Test
  fun `should skip logo download when logo already fetched`() {
    val etf = mockk<Instrument>()
    every { etf.id } returns 100L
    val holding = createHolding(1L, "AAPL", "Apple Inc", logoFetched = true)
    val positionSlot = slot<ee.tenman.portfolio.domain.EtfPosition>()

    every { instrumentRepository.findBySymbol("VWCE") } returns Optional.of(etf)
    every { etfHoldingRepository.findByNameIgnoreCase("Apple Inc") } returns Optional.of(holding)
    every { etfPositionRepository.findByEtfInstrumentAndHoldingIdAndSnapshotDate(any(), any(), any()) } returns null
    every { etfPositionRepository.save(capture(positionSlot)) } answers { positionSlot.captured }

    val holdingData =
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = null,
        weight = BigDecimal.TEN,
        rank = 1,
        logoUrl = "https://lightyear.com/logo.png",
      )
    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    verify(exactly = 0) { imageDownloadService.download(any()) }
  }

  @Test
  fun `should handle download failure gracefully`() {
    val etf = mockk<Instrument>()
    every { etf.id } returns 100L
    val holding = createHolding(1L, "TSLA", "Tesla Inc")
    val positionSlot = slot<ee.tenman.portfolio.domain.EtfPosition>()

    every { instrumentRepository.findBySymbol("VWCE") } returns Optional.of(etf)
    every { etfHoldingRepository.findByNameIgnoreCase("Tesla Inc") } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns false
    every { imageDownloadService.download("https://lightyear.com/tesla.png") } throws RuntimeException("Network error")
    every { etfPositionRepository.findByEtfInstrumentAndHoldingIdAndSnapshotDate(any(), any(), any()) } returns null
    every { etfPositionRepository.save(capture(positionSlot)) } answers { positionSlot.captured }

    val holdingData =
      HoldingData(
        name = "Tesla Inc",
        ticker = "TSLA",
        sector = null,
        weight = BigDecimal.TEN,
        rank = 1,
        logoUrl = "https://lightyear.com/tesla.png",
      )
    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    expect(holding.logoFetched).toEqual(false)
    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
  }

  @Test
  fun `should mark logo fetched when logo already exists in MinIO`() {
    val etf = mockk<Instrument>()
    every { etf.id } returns 100L
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    val positionSlot = slot<ee.tenman.portfolio.domain.EtfPosition>()

    every { instrumentRepository.findBySymbol("VWCE") } returns Optional.of(etf)
    every { etfHoldingRepository.findByNameIgnoreCase("Apple Inc") } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns true
    every { etfPositionRepository.findByEtfInstrumentAndHoldingIdAndSnapshotDate(any(), any(), any()) } returns null
    every { etfPositionRepository.save(capture(positionSlot)) } answers { positionSlot.captured }

    val holdingData =
      HoldingData(
        name = "Apple Inc",
        ticker = "AAPL",
        sector = null,
        weight = BigDecimal.TEN,
        rank = 1,
        logoUrl = "https://lightyear.com/logo.png",
      )
    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    expect(holding.logoFetched).toEqual(true)
    verify(exactly = 0) { imageDownloadService.download(any()) }
  }

  private fun createHolding(
    id: Long,
    ticker: String?,
    name: String,
    logoFetched: Boolean = false,
  ): EtfHolding =
    EtfHolding(ticker = ticker, name = name).apply {
      this.id = id
      this.logoFetched = logoFetched
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
