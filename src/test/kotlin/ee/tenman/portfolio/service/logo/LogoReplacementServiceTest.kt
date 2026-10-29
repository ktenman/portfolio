package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.LogoReplacementProperties
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.service.instrument.InstrumentService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class LogoReplacementServiceTest {
  private val etfHoldingRepository = mockk<EtfHoldingRepository>()
  private val imageSearchLogoService = mockk<ImageSearchLogoService>()
  private val imageDownloadService = mockk<ImageDownloadService>()
  private val logoValidationService = mockk<LogoValidationService>()
  private val imageProcessingService = mockk<ImageProcessingService>()
  private val minioService = mockk<MinioService>(relaxed = true)
  private val logoCandidateCacheService = mockk<LogoCandidateCacheService>()
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val instrumentService = mockk<InstrumentService>(relaxed = true)
  private val properties = LogoReplacementProperties()
  private lateinit var service: LogoReplacementService

  @BeforeEach
  fun setup() {
    service =
      LogoReplacementService(
        LogoOwnerService(etfHoldingRepository, instrumentRepository, instrumentService, minioService),
        imageSearchLogoService,
        imageDownloadService,
        logoValidationService,
        imageProcessingService,
        logoCandidateCacheService,
        properties,
      )
  }

  @Nested
  inner class GetCandidates {
    @Test
    fun `should return cached candidates when available`() {
      val uuid = UUID.randomUUID()
      val imageData = "test-image".toByteArray()
      val cachedCandidate = LogoCandidate(thumbnailUrl = "thumb.png", imageUrl = "img.png", title = "Apple", index = 0)
      val cachedData = CachedLogoData(candidates = listOf(cachedCandidate), images = mapOf(0 to imageData))
      every { logoCandidateCacheService.getCachedData(uuid) } returns cachedData
      every { logoValidationService.detectMediaType(any()) } returns "image/png"

      val result = service.getCandidates(uuid)

      expect(result).toHaveSize(1)
      expect(result[0].title).toEqual("Apple")
      expect(result[0].index).toEqual(0)
    }

    @Test
    fun `should return empty list when neither a holding nor an instrument has the uuid`() {
      val uuid = UUID.randomUUID()
      every { logoCandidateCacheService.getCachedData(uuid) } returns null
      every { etfHoldingRepository.findByUuid(uuid) } returns null
      every { instrumentRepository.findByUuid(uuid) } returns null

      val result = service.getCandidates(uuid)

      expect(result).toHaveSize(0)
    }

    @Test
    fun `should search the logo of the issuer of an instrument`() {
      val instrument = createInstrument("GB00B0ZDNB53:GBP", "Aviva BlackRock Aquila US Equity Index Tracker 6 Pension Fund")
      every { logoCandidateCacheService.getCachedData(instrument.uuid) } returns null
      every { etfHoldingRepository.findByUuid(instrument.uuid) } returns null
      every { instrumentRepository.findByUuid(instrument.uuid) } returns instrument
      every { imageSearchLogoService.searchLogoCandidates(any(), 50) } returns emptyList()

      service.getCandidates(instrument.uuid)

      verify { imageSearchLogoService.searchLogoCandidates("Aviva logo", 50) }
    }

    @Test
    fun `should return empty list when no search results`() {
      val uuid = UUID.randomUUID()
      val holding = createHolding(uuid, "Apple Inc", "AAPL")
      every { logoCandidateCacheService.getCachedData(uuid) } returns null
      every { etfHoldingRepository.findByUuid(uuid) } returns holding
      every { imageSearchLogoService.searchLogoCandidates("AAPL Apple Inc logo", 50) } returns emptyList()

      val result = service.getCandidates(uuid)

      expect(result).toHaveSize(0)
    }
  }

  @Nested
  inner class ReplaceLogo {
    @Test
    fun `should return false when no cached data`() {
      val uuid = UUID.randomUUID()
      every { logoCandidateCacheService.getCachedData(uuid) } returns null

      val result = service.replaceLogo(uuid, 0)

      expect(result).toEqual(false)
    }

    @Test
    fun `should return false when candidate index not found`() {
      val uuid = UUID.randomUUID()
      val cachedCandidate = LogoCandidate(thumbnailUrl = "thumb.png", imageUrl = "img.png", title = "Apple", index = 0)
      val cachedData = CachedLogoData(candidates = listOf(cachedCandidate), images = mapOf())
      every { logoCandidateCacheService.getCachedData(uuid) } returns cachedData

      val result = service.replaceLogo(uuid, 999)

      expect(result).toEqual(false)
    }

    @Test
    fun `should replace logo successfully using cached image`() {
      val uuid = UUID.randomUUID()
      val imageData = "test-image".toByteArray()
      val processedImage = "processed".toByteArray()
      val cachedCandidate = LogoCandidate(thumbnailUrl = "thumb.png", imageUrl = "img.png", title = "Apple", index = 0)
      val cachedData = CachedLogoData(candidates = listOf(cachedCandidate), images = mapOf(0 to imageData))
      val holding = createHolding(uuid, "Apple Inc", "AAPL")
      every { logoCandidateCacheService.getCachedData(uuid) } returns cachedData
      every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
      every { etfHoldingRepository.findByUuid(uuid) } returns holding
      every { etfHoldingRepository.save(holding) } returns holding
      every { logoCandidateCacheService.clearCache(uuid) } returns Unit

      val result = service.replaceLogo(uuid, 0)

      expect(result).toEqual(true)
      expect(holding.logoSource).toEqual(LogoSource.MANUAL)
      verify { minioService.uploadLogo(uuid, processedImage) }
      verify { logoCandidateCacheService.clearCache(uuid) }
    }

    @Test
    fun `should store the chosen logo of an instrument as a manual logo`() {
      val instrument = createInstrument("BNKE:PAR:EUR", "Amundi Euro Stoxx Banks")
      val imageData = "test-image".toByteArray()
      val processedImage = "processed".toByteArray()
      val cachedCandidate = LogoCandidate(thumbnailUrl = "thumb.png", imageUrl = "img.png", title = "Amundi", index = 0)
      val cachedData = CachedLogoData(candidates = listOf(cachedCandidate), images = mapOf(0 to imageData))
      every { logoCandidateCacheService.getCachedData(instrument.uuid) } returns cachedData
      every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
      every { etfHoldingRepository.findByUuid(instrument.uuid) } returns null
      every { instrumentRepository.findByUuid(instrument.uuid) } returns instrument
      every { logoCandidateCacheService.clearCache(instrument.uuid) } returns Unit

      service.replaceLogo(instrument.uuid, 0)

      verifyOrder {
        minioService.uploadLogo(instrument.uuid, processedImage)
        instrumentService.updateLogoSource(5L, "BNKE:PAR:EUR", LogoSource.MANUAL)
      }
    }

    @Test
    fun `should return false without uploading logo when neither a holding nor an instrument has the uuid`() {
      val uuid = UUID.randomUUID()
      val imageData = "test-image".toByteArray()
      val processedImage = "processed".toByteArray()
      val cachedCandidate = LogoCandidate(thumbnailUrl = "thumb.png", imageUrl = "img.png", title = "Apple", index = 0)
      val cachedData = CachedLogoData(candidates = listOf(cachedCandidate), images = mapOf(0 to imageData))
      every { logoCandidateCacheService.getCachedData(uuid) } returns cachedData
      every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
      every { etfHoldingRepository.findByUuid(uuid) } returns null
      every { instrumentRepository.findByUuid(uuid) } returns null

      val result = service.replaceLogo(uuid, 0)

      expect(result).toEqual(false)
      verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
    }
  }

  @Nested
  inner class SearchByName {
    @Test
    fun `should return cached candidates when available`() {
      val imageData = "test-image".toByteArray()
      val cachedCandidate = LogoCandidate(thumbnailUrl = "thumb.png", imageUrl = "img.png", title = "Apple", index = 0)
      val cachedData = CachedLogoData(candidates = listOf(cachedCandidate), images = mapOf(0 to imageData))
      every { logoCandidateCacheService.getCachedDataByName("Apple Inc") } returns cachedData
      every { logoValidationService.detectMediaType(any()) } returns "image/png"

      val result = service.searchByName("Apple Inc")

      expect(result).toHaveSize(1)
      expect(result[0].title).toEqual("Apple")
    }

    @Test
    fun `should return empty list when no search results`() {
      every { logoCandidateCacheService.getCachedDataByName("Apple Inc") } returns null
      every { imageSearchLogoService.searchLogoCandidates("Apple Inc logo", 50) } returns emptyList()

      val result = service.searchByName("Apple Inc")

      expect(result).toHaveSize(0)
    }

    @Test
    fun `should return validated candidates for name search`() {
      val imageData = "test-image".toByteArray()
      val candidate = LogoCandidate(thumbnailUrl = "thumb.png", imageUrl = "img.png", title = "Apple", index = 0)
      every { logoCandidateCacheService.getCachedDataByName("Microsoft") } returns null
      every { imageSearchLogoService.searchLogoCandidates("Microsoft logo", 50) } returns listOf(candidate)
      every { imageDownloadService.downloadOrNull("img.png") } returns imageData
      every { logoValidationService.isValidLogo(imageData) } returns true
      every { logoValidationService.detectMediaType(imageData) } returns "image/png"
      every { logoCandidateCacheService.cacheByName("Microsoft", listOf(candidate), mapOf(0 to imageData)) } returns
        CachedLogoData(candidates = listOf(candidate), images = mapOf(0 to imageData))

      val result = service.searchByName("Microsoft")

      expect(result).toHaveSize(1)
      expect(result[0].title).toEqual("Apple")
      expect(result[0].index).toEqual(0)
      verify { logoCandidateCacheService.cacheByName("Microsoft", listOf(candidate), mapOf(0 to imageData)) }
    }
  }

  private fun createInstrument(
    symbol: String,
    name: String,
  ): Instrument = Instrument(symbol = symbol, name = name, category = "ETF", baseCurrency = "EUR").apply { id = 5L }

  private fun createHolding(
    uuid: UUID,
    name: String,
    ticker: String?,
  ): EtfHolding =
    EtfHolding(ticker = ticker, name = name).apply {
      this.id = 1L
      this.uuid = uuid
    }
}
