package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class EtfHoldingServiceTest {
  private val etfHoldingPersistenceService = mockk<EtfHoldingPersistenceService>()
  private val minioService = mockk<MinioService>()
  private val imageDownloadService = mockk<ImageDownloadService>()
  private val imageProcessingService = mockk<ImageProcessingService>()
  private lateinit var service: EtfHoldingService
  private val testDate = LocalDate.of(2024, 1, 15)

  @BeforeEach
  fun setup() {
    service =
      EtfHoldingService(
        etfHoldingPersistenceService,
        minioService,
        imageDownloadService,
        imageProcessingService,
      )
  }

  @Test
  fun `should return existing holding when found by name`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    every { etfHoldingPersistenceService.findOrCreateHolding("Apple Inc", "AAPL", null) } returns holding

    val result = service.findOrCreateHolding("Apple Inc", "AAPL", null)

    expect(result.id).toEqual(1L)
    expect(result.name).toEqual("Apple Inc")
  }

  @Test
  fun `should create new holding when not found`() {
    val savedHolding = createHolding(2L, "NVDA", "NVIDIA Corp")
    every { etfHoldingPersistenceService.findOrCreateHolding("NVIDIA Corp", "NVDA", "Technology") } returns savedHolding

    val result = service.findOrCreateHolding("NVIDIA Corp", "NVDA", "Technology")

    expect(result.id).toEqual(2L)
  }

  @Test
  fun `should update ticker when existing holding has no ticker`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    every { etfHoldingPersistenceService.findOrCreateHolding("Apple Inc", "AAPL", null) } returns holding

    val result = service.findOrCreateHolding("Apple Inc", "AAPL", null)

    expect(result.ticker).toEqual("AAPL")
  }

  @Test
  fun `should not update ticker when existing holding already has ticker`() {
    val holding = createHolding(1L, "AAPL", "Apple Inc")
    every { etfHoldingPersistenceService.findOrCreateHolding("Apple Inc", "DIFFERENT", null) } returns holding

    val result = service.findOrCreateHolding("Apple Inc", "DIFFERENT", null)

    expect(result.ticker).toEqual("AAPL")
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
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(holdingData)) } returns mapOf("NVIDIA Corp" to holding)
    every { imageDownloadService.download("https://lightyear.com/logo.png") } returns imageData
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
    every { minioService.uploadLogo(holdingUuid, processedImage) } returns Unit
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
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(holdingData)) } returns mapOf("Apple Inc" to holding)

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
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(holdingData)) } returns mapOf("Apple Inc" to holding)

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
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(holdingData)) } returns mapOf("Apple Inc" to holding)
    every { imageDownloadService.download("https://lightyear.com/logo.png") } returns imageData
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
    every { minioService.uploadLogo(holdingUuid, processedImage) } returns Unit
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
    every { etfHoldingPersistenceService.saveHoldings("VWCE", testDate, listOf(holdingData)) } returns mapOf("Tesla Inc" to holding)
    every { imageDownloadService.download("https://lightyear.com/tesla.png") } throws RuntimeException("Network error")

    service.saveHoldings("VWCE", testDate, listOf(holdingData))

    expect(holding.logoSource).toEqual(null)
    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
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
