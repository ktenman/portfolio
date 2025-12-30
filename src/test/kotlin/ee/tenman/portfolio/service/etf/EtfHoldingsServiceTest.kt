package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
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
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional

class EtfHoldingsServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val etfHoldingRepository = mockk<EtfHoldingRepository>()
  private val etfPositionRepository = mockk<EtfPositionRepository>()
  private val minioService = mockk<MinioService>()
  private val imageDownloadService = mockk<ImageDownloadService>()
  private val imageProcessingService = mockk<ImageProcessingService>()
  private lateinit var service: EtfHoldingsService

  @BeforeEach
  fun setup() {
    service = EtfHoldingsService(
      instrumentRepository,
      etfHoldingRepository,
      etfPositionRepository,
      minioService,
      imageDownloadService,
      imageProcessingService,
    )
  }

  @Test
  fun `should skip logo download when logoUrl is null`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    every { etfHoldingRepository.findByNameIgnoreCase("Apple Inc") } returns Optional.of(holding)

    val holdingData = createHoldingData("Apple Inc", "AAPL", null)
    service.findOrCreateHolding(holdingData.name, holdingData.ticker, holdingData.sector)

    verify(exactly = 0) { imageDownloadService.download(any()) }
    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
  }

  @Test
  fun `should skip logo download when logoUrl is blank`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    every { etfHoldingRepository.findByNameIgnoreCase("Apple Inc") } returns Optional.of(holding)

    val holdingData = createHoldingData("Apple Inc", "AAPL", "  ")
    service.findOrCreateHolding(holdingData.name, holdingData.ticker, holdingData.sector)

    verify(exactly = 0) { imageDownloadService.download(any()) }
  }

  @Test
  fun `should skip logo download when logo already fetched`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc", logoFetched = true)
    every { etfHoldingRepository.findByNameIgnoreCase("Apple Inc") } returns Optional.of(holding)

    service.findOrCreateHolding("Apple Inc", "AAPL", null)

    verify(exactly = 0) { imageDownloadService.download(any()) }
  }

  @Test
  fun `should mark logo fetched when logo exists in MinIO`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    every { etfHoldingRepository.findByNameIgnoreCase("Apple Inc") } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns true

    service.findOrCreateHolding("Apple Inc", "AAPL", null)

    verify(exactly = 0) { imageDownloadService.download(any()) }
  }

  @Test
  fun `should download and upload logo when url provided and logo does not exist`() {
    val holding = createHolding(1L, "NVDA", "NVIDIA Corp")
    val imageData = "image-bytes".toByteArray()
    val processedImage = "processed-bytes".toByteArray()

    every { etfHoldingRepository.findByNameIgnoreCase("NVIDIA Corp") } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns false
    every { imageDownloadService.download("https://lightyear.com/logo.png") } returns imageData
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
    every { minioService.uploadLogo(1L, processedImage) } returns Unit

    service.findOrCreateHolding("NVIDIA Corp", "NVDA", null)

    expect(holding.logoFetched).toEqual(true)
    expect(holding.logoSource).toEqual(LogoSource.LIGHTYEAR)
    verify { minioService.uploadLogo(1L, processedImage) }
  }

  @Test
  fun `should handle download failure gracefully`() {
    val holding = createHolding(1L, "TSLA", "Tesla Inc")

    every { etfHoldingRepository.findByNameIgnoreCase("Tesla Inc") } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns false
    every { imageDownloadService.download("https://lightyear.com/tesla.png") } throws RuntimeException("Network error")

    service.findOrCreateHolding("Tesla Inc", "TSLA", null)

    expect(holding.logoFetched).toEqual(false)
    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
  }

  @Test
  fun `should handle upload failure gracefully`() {
    val holding = createHolding(1L, "AMD", "AMD Inc")
    val imageData = "image-bytes".toByteArray()
    val processedImage = "processed-bytes".toByteArray()

    every { etfHoldingRepository.findByNameIgnoreCase("AMD Inc") } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns false
    every { imageDownloadService.download("https://lightyear.com/amd.png") } returns imageData
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
    every { minioService.uploadLogo(1L, processedImage) } throws RuntimeException("MinIO error")

    service.findOrCreateHolding("AMD Inc", "AMD", null)

    expect(holding.logoFetched).toEqual(false)
  }

  private fun createHolding(
    id: Long,
    ticker: String?,
    name: String,
    logoFetched: Boolean = false,
  ): EtfHolding = EtfHolding(ticker = ticker, name = name).apply {
    this.id = id
    this.logoFetched = logoFetched
  }

  private fun createHoldingData(
    name: String,
    ticker: String?,
    logoUrl: String?,
  ): HoldingData = HoldingData(
    name = name,
    ticker = ticker,
    sector = null,
    weight = BigDecimal.TEN,
    rank = 1,
    logoUrl = logoUrl,
  )
}
