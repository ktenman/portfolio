package ee.tenman.portfolio.service.logo

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.LogoReplacementProperties
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
  private val logoCacheService = mockk<LogoCacheService>()
  private val logoCandidateCacheService = mockk<LogoCandidateCacheService>()
  private val properties = LogoReplacementProperties()
  private lateinit var service: LogoReplacementService

  @BeforeEach
  fun setup() {
    service =
      LogoReplacementService(
        etfHoldingRepository,
        imageSearchLogoService,
        imageDownloadService,
        logoValidationService,
        imageProcessingService,
        logoCacheService,
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
    fun `should return empty list when holding not found`() {
      val uuid = UUID.randomUUID()
      every { logoCandidateCacheService.getCachedData(uuid) } returns null
      every { etfHoldingRepository.findByUuid(uuid) } returns null

      val result = service.getCandidates(uuid)

      expect(result).toHaveSize(0)
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
      every { logoCacheService.saveLogo(uuid, processedImage) } returns processedImage
      every { etfHoldingRepository.findByUuid(uuid) } returns holding
      every { etfHoldingRepository.save(holding) } returns holding
      every { logoCandidateCacheService.clearCache(uuid) } returns Unit

      val result = service.replaceLogo(uuid, 0)

      expect(result).toEqual(true)
      expect(holding.logoSource).toEqual(LogoSource.MANUAL)
      verify { logoCacheService.saveLogo(uuid, processedImage) }
      verify { logoCandidateCacheService.clearCache(uuid) }
    }

    @Test
    fun `should return false when holding not found after processing`() {
      val uuid = UUID.randomUUID()
      val imageData = "test-image".toByteArray()
      val processedImage = "processed".toByteArray()
      val cachedCandidate = LogoCandidate(thumbnailUrl = "thumb.png", imageUrl = "img.png", title = "Apple", index = 0)
      val cachedData = CachedLogoData(candidates = listOf(cachedCandidate), images = mapOf(0 to imageData))
      every { logoCandidateCacheService.getCachedData(uuid) } returns cachedData
      every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage
      every { logoCacheService.saveLogo(uuid, processedImage) } returns processedImage
      every { etfHoldingRepository.findByUuid(uuid) } returns null

      val result = service.replaceLogo(uuid, 0)

      expect(result).toEqual(false)
    }
  }

  @Nested
  inner class PrefetchCandidates {
    @Test
    fun `should prefetch candidates for multiple holdings`() {
      val uuid1 = UUID.randomUUID()
      val uuid2 = UUID.randomUUID()
      every { logoCandidateCacheService.getCachedData(uuid1) } returns null
      every { logoCandidateCacheService.getCachedData(uuid2) } returns null
      every { etfHoldingRepository.findByUuid(uuid1) } returns null
      every { etfHoldingRepository.findByUuid(uuid2) } returns null

      service.prefetchCandidates(listOf(uuid1, uuid2))
    }

    @Test
    fun `should handle empty list gracefully`() {
      service.prefetchCandidates(emptyList())
    }
  }

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
