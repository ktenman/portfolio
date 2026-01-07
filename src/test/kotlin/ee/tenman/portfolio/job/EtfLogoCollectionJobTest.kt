package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.logo.LogoCacheService
import ee.tenman.portfolio.service.logo.LogoFallbackService
import ee.tenman.portfolio.service.logo.LogoFetchResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class EtfLogoCollectionJobTest {
  private val etfHoldingRepository: EtfHoldingRepository = mockk(relaxed = true)
  private val logoFallbackService: LogoFallbackService = mockk()
  private val logoCacheService: LogoCacheService = mockk(relaxed = true)
  private val imageProcessingService: ImageProcessingService = mockk()

  private val job =
    EtfLogoCollectionJob(
      etfHoldingRepository = etfHoldingRepository,
      logoFallbackService = logoFallbackService,
      logoCacheService = logoCacheService,
      imageProcessingService = imageProcessingService,
    )

  @Test
  fun `should fetch and upload logo when logo source is null`() {
    val holdingUuid = UUID.randomUUID()
    val holding = createHolding(id = 1L, name = "Apple Inc", ticker = "AAPL", uuid = holdingUuid, countryCode = "US")
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    val processedImage = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00)
    val logoResult = LogoFetchResult(imageData = imageData, source = LogoSource.NVSTLY_ICONS)
    every { logoFallbackService.fetchLogo("Apple Inc", "AAPL", null) } returns logoResult
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage

    job.processHolding(holding)

    verify(exactly = 1) { logoCacheService.saveLogo(holdingUuid, processedImage) }
    verify(exactly = 1) { etfHoldingRepository.save(holding) }
    expect(holding.logoSource).toEqual(LogoSource.NVSTLY_ICONS)
  }

  @Test
  fun `should not save when no logo found from any source`() {
    val holding = createHolding(id = 1L, name = "Unknown Corp", countryCode = "US")
    every { logoFallbackService.fetchLogo("Unknown Corp", null, null) } returns null

    job.processHolding(holding)

    verify(exactly = 0) { logoCacheService.saveLogo(any(), any()) }
    verify(exactly = 0) { etfHoldingRepository.save(any()) }
    expect(holding.logoSource).toEqual(null)
  }

  @Test
  fun `should handle errors gracefully`() {
    val holding = createHolding(id = 1L, name = "Error Corp", countryCode = "US")
    every { logoFallbackService.fetchLogo(any(), any(), any()) } throws RuntimeException("Network error")

    job.processHolding(holding)

    verify(exactly = 0) { logoCacheService.saveLogo(any(), any()) }
    verify(exactly = 0) { etfHoldingRepository.save(any()) }
  }

  @Test
  fun `should process holding with ticker symbol`() {
    val holdingUuid = UUID.randomUUID()
    val holding = createHolding(id = 2L, name = "Microsoft", ticker = "MSFT", uuid = holdingUuid, countryCode = "US")
    val imageData = byteArrayOf(0x89.toByte())
    val logoResult = LogoFetchResult(imageData = imageData, source = LogoSource.BING)
    every { logoFallbackService.fetchLogo("Microsoft", "MSFT", null) } returns logoResult
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns imageData

    job.processHolding(holding)

    verify(exactly = 1) { logoCacheService.saveLogo(holdingUuid, imageData) }
    expect(holding.logoSource).toEqual(LogoSource.BING)
  }

  private fun createHolding(
    id: Long = 1L,
    name: String = "Test Company",
    ticker: String? = null,
    logoSource: LogoSource? = null,
    uuid: UUID? = null,
    countryCode: String? = null,
  ): EtfHolding =
    EtfHolding(name = name, ticker = ticker).apply {
      this.id = id
      this.logoSource = logoSource
      this.countryCode = countryCode
      if (uuid != null) this.uuid = uuid
    }
}
