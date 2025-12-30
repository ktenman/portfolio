package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.service.logo.LogoFallbackService
import ee.tenman.portfolio.service.logo.LogoFetchResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional

class EtfLogoCollectionJobTest {
  private val etfHoldingRepository: EtfHoldingRepository = mockk(relaxed = true)
  private val logoFallbackService: LogoFallbackService = mockk()
  private val minioService: MinioService = mockk(relaxed = true)
  private val imageProcessingService: ImageProcessingService = mockk()

  private val job =
    EtfLogoCollectionJob(
      etfHoldingRepository = etfHoldingRepository,
      logoFallbackService = logoFallbackService,
      minioService = minioService,
      imageProcessingService = imageProcessingService,
    )

  @Test
  fun `should skip processing when holding is already fetched`() {
    val holding = createHolding(id = 1L, name = "Apple Inc", logoFetched = true)
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)

    job.processHolding(1L)

    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
    verify(exactly = 0) { minioService.uploadLogo(any(), any(), any()) }
  }

  @Test
  fun `should mark as fetched when logo already exists in minio`() {
    val holding = createHolding(id = 1L, name = "Apple Inc")
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns true

    job.processHolding(1L)

    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
    verify(exactly = 1) { etfHoldingRepository.save(holding) }
    expect(holding.logoFetched).toEqual(true)
  }

  @Test
  fun `should fetch and upload logo when not exists`() {
    val holding = createHolding(id = 1L, name = "Apple Inc", ticker = "AAPL")
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    val processedImage = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00)
    val logoResult = LogoFetchResult(imageData = imageData, source = LogoSource.NVSTLY_ICONS)
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns false
    every { logoFallbackService.fetchLogo("Apple Inc", "AAPL", null) } returns logoResult
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage

    job.processHolding(1L)

    verify(exactly = 1) { minioService.uploadLogo(1L, processedImage) }
    verify(exactly = 1) { etfHoldingRepository.save(holding) }
    expect(holding.logoFetched).toEqual(true)
    expect(holding.logoSource).toEqual(LogoSource.NVSTLY_ICONS)
  }

  @Test
  fun `should mark as fetched when no logo found`() {
    val holding = createHolding(id = 1L, name = "Unknown Corp")
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns false
    every { logoFallbackService.fetchLogo("Unknown Corp", null, null) } returns null

    job.processHolding(1L)

    verify(exactly = 0) { minioService.uploadLogo(any(), any(), any()) }
    verify(exactly = 1) { etfHoldingRepository.save(holding) }
    expect(holding.logoFetched).toEqual(true)
    expect(holding.logoSource).toEqual(null)
  }

  @Test
  fun `should update ticker when extracted from logo service`() {
    val holding = createHolding(id = 1L, name = "Apple Inc", ticker = null)
    val imageData = byteArrayOf(0x89.toByte(), 0x50)
    val logoResult = LogoFetchResult(imageData = imageData, source = LogoSource.BING, ticker = "AAPL")
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns false
    every { logoFallbackService.fetchLogo("Apple Inc", null, null) } returns logoResult
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns imageData

    job.processHolding(1L)

    expect(holding.ticker).toEqual("AAPL")
  }

  @Test
  fun `should not update ticker when holding already has one`() {
    val holding = createHolding(id = 1L, name = "Apple Inc", ticker = "EXISTING")
    val imageData = byteArrayOf(0x89.toByte(), 0x50)
    val logoResult = LogoFetchResult(imageData = imageData, source = LogoSource.BING, ticker = "AAPL")
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)
    every { minioService.logoExists(1L) } returns false
    every { logoFallbackService.fetchLogo("Apple Inc", "EXISTING", null) } returns logoResult
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns imageData

    job.processHolding(1L)

    expect(holding.ticker).toEqual("EXISTING")
  }

  @Test
  fun `should handle errors gracefully`() {
    every { etfHoldingRepository.findById(1L) } throws RuntimeException("Database error")

    job.processHolding(1L)

    verify(exactly = 0) { minioService.uploadLogo(any(), any(), any()) }
  }

  @Test
  fun `should skip when holding not found`() {
    every { etfHoldingRepository.findById(1L) } returns Optional.empty()

    job.processHolding(1L)

    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
  }

  private fun createHolding(
    id: Long = 1L,
    name: String = "Test Company",
    ticker: String? = null,
    logoFetched: Boolean = false,
  ): EtfHolding =
    EtfHolding(name = name, ticker = ticker).apply {
      this.id = id
      this.logoFetched = logoFetched
    }
}
