package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.exception.LogoSearchUnavailableException
import ee.tenman.portfolio.exception.LogoSelectionFailedException
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.service.logo.LogoFallbackService
import ee.tenman.portfolio.service.logo.LogoFetchResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import javax.imageio.IIOException

class EtfLogoCollectionJobTest {
  private val etfHoldingRepository: EtfHoldingRepository = mockk(relaxed = true)
  private val logoFallbackService: LogoFallbackService = mockk()
  private val minioService: MinioService = mockk(relaxed = true)
  private val imageProcessingService: ImageProcessingService = mockk()
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService = mockk(relaxed = true)
  private val jobTransactionService: JobTransactionService = mockk(relaxed = true)

  private val job =
    EtfLogoCollectionJob(
      etfHoldingRepository = etfHoldingRepository,
      logoFallbackService = logoFallbackService,
      minioService = minioService,
      imageProcessingService = imageProcessingService,
      etfHoldingPersistenceService = etfHoldingPersistenceService,
      jobTransactionService = jobTransactionService,
      clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

  @Test
  fun `should skip processing when holding already has logo source`() {
    val holding = createHolding(id = 1L, name = "Apple Inc", logoSource = LogoSource.LIGHTYEAR)
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)

    job.processHolding(1L)

    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
  }

  @Test
  fun `should fetch and upload logo when logo source is null`() {
    val holdingUuid = UUID.randomUUID()
    val holding = createHolding(id = 1L, name = "Apple Inc", ticker = "AAPL", uuid = holdingUuid, countryCode = "US")
    val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    val processedImage = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00)
    val logoResult = LogoFetchResult(imageData = imageData, source = LogoSource.NVSTLY_ICONS)
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)
    every { logoFallbackService.fetchLogo("Apple Inc", "AAPL", null) } returns logoResult
    every { imageProcessingService.resizeToMaxDimension(imageData) } returns processedImage

    job.processHolding(1L)

    verify(exactly = 1) { minioService.uploadLogo(holdingUuid, processedImage) }
    verify(exactly = 1) { etfHoldingRepository.save(holding) }
    expect(holding.logoSource).toEqual(LogoSource.NVSTLY_ICONS)
  }

  @Test
  fun `should skip logo fetch when holding has no country code`() {
    val holding = createHolding(id = 1L, name = "Unknown Corp", ticker = "UNK")
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)

    job.processHolding(1L)

    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
  }

  @Test
  fun `should not save when no logo found from any source`() {
    val holding = createHolding(id = 1L, name = "Unknown Corp", countryCode = "US")
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)
    every { logoFallbackService.fetchLogo("Unknown Corp", null, null) } returns null

    job.processHolding(1L)

    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
    verify(exactly = 0) { etfHoldingRepository.save(any()) }
    expect(holding.logoSource).toEqual(null)
  }

  @Test
  fun `should handle errors gracefully`() {
    every { etfHoldingRepository.findById(1L) } throws RuntimeException("Database error")

    job.processHolding(1L)

    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
  }

  @Test
  fun `should skip when holding not found`() {
    every { etfHoldingRepository.findById(1L) } returns Optional.empty()

    job.processHolding(1L)

    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
  }

  @Test
  fun `cannot look up a logo by the ticker of a holding listed outside the United States`() {
    val holding = createHolding(id = 1L, name = "Allianz SE", ticker = "ALV", countryCode = "DE")
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)
    every { logoFallbackService.fetchLogo("Allianz SE", null, null) } returns null

    job.processHolding(1L)

    verify(exactly = 1) { logoFallbackService.fetchLogo("Allianz SE", null, null) }
  }

  @Test
  fun `should look up a logo by the ticker of a holding listed in the United States`() {
    val holding = createHolding(id = 1L, name = "Autoliv Inc", ticker = "ALV", countryCode = "US")
    every { etfHoldingRepository.findById(1L) } returns Optional.of(holding)
    every { logoFallbackService.fetchLogo("Autoliv Inc", "ALV", null) } returns null

    job.processHolding(1L)

    verify(exactly = 1) { logoFallbackService.fetchLogo("Autoliv Inc", "ALV", null) }
  }

  @Test
  fun `should ask for holdings with attempts left or last searched 90 days ago`() {
    every { etfHoldingRepository.findHoldingsWithoutLogosForCurrentPortfolio(any(), any()) } returns emptyList()

    job.collectMissingLogos()

    verify(exactly = 1) { etfHoldingRepository.findHoldingsWithoutLogosForCurrentPortfolio(3, NOW.minus(Duration.ofDays(90))) }
  }

  @Test
  fun `should use no attempt when a logo is saved`() {
    queue(finding("Põhja Pank"))
    every { imageProcessingService.resizeToMaxDimension(any()) } returns byteArrayOf(0x50)
    every { etfHoldingRepository.save(any<EtfHolding>()) } answers { firstArg() }
    val outcome = job.processHolding(1L)
    verify(exactly = 0) { etfHoldingPersistenceService.incrementLogoFetchAttempts(1L, NOW) }
    expect(outcome).toEqual(LogoOutcome.Answered)
  }

  @Test
  fun `should use an attempt when nothing is found`() {
    queue(answering("Põhja Pank"))
    val outcome = job.processHolding(1L)
    verify(exactly = 1) { etfHoldingPersistenceService.incrementLogoFetchAttempts(1L, NOW) }
    expect(outcome).toEqual(LogoOutcome.Answered)
  }

  @Test
  fun `should use an attempt and fail when logo selection fails`() {
    queue(failing("Põhja Pank", LogoSelectionFailedException("Vision API returned a blank reply for Põhja Pank")))
    val outcome = job.processHolding(1L)
    verify(exactly = 1) { etfHoldingPersistenceService.incrementLogoFetchAttempts(1L, NOW) }
    expect(outcome).toEqual(LogoOutcome.Failed("Põhja Pank: Vision API returned a blank reply for Põhja Pank"))
  }

  @Test
  fun `should use no attempt and fail when search is unavailable`() {
    queue(failing("Põhja Pank"))
    val outcome = job.processHolding(1L)
    verify(exactly = 0) { etfHoldingPersistenceService.incrementLogoFetchAttempts(1L, NOW) }
    expect(outcome).toEqual(LogoOutcome.Failed("Põhja Pank: blocked"))
  }

  @Test
  fun `should use no attempt and fail when the upload fails`() {
    val holding = finding("Põhja Pank")
    queue(holding)
    every { imageProcessingService.resizeToMaxDimension(any()) } returns byteArrayOf(0x50)
    every { minioService.uploadLogo(any(), any()) } throws IllegalStateException("Bucket is gone")
    val outcome = job.processHolding(1L)
    verify(exactly = 0) { etfHoldingPersistenceService.incrementLogoFetchAttempts(1L, NOW) }
    expect(holding.logoSource).toEqual(null)
    expect(outcome).toEqual(LogoOutcome.Failed("Holding 1: Bucket is gone"))
  }

  @Test
  fun `should use an attempt when the chosen logo cannot be decoded`() {
    queue(finding("Põhja Pank"))
    every { imageProcessingService.resizeToMaxDimension(any()) } throws IIOException("Unsupported Image Type")
    val outcome = job.processHolding(1L)
    verify(exactly = 1) { etfHoldingPersistenceService.incrementLogoFetchAttempts(1L, NOW) }
    verify(exactly = 0) { minioService.uploadLogo(any(), any()) }
    expect(outcome).toEqual(LogoOutcome.Answered)
  }

  @Test
  fun `should use no attempt when the holding has no country`() {
    queue(skipped("Põhja Pank"))
    val outcome = job.processHolding(1L)
    verify(exactly = 0) { logoFallbackService.fetchLogo(any(), any(), any()) }
    verify(exactly = 0) { etfHoldingPersistenceService.incrementLogoFetchAttempts(1L, NOW) }
    expect(outcome).toEqual(LogoOutcome.Skipped)
  }

  @Test
  fun `should stop after three failures in a row`() {
    queue(failing("Põhja Pank"), failing("Šveitsi Pank"), failing("Ühispank"), answering("Õnne AS"))
    job.collectMissingLogos()
    verify(exactly = 0) { logoFallbackService.fetchLogo("Õnne AS", any(), any()) }
    verify { jobTransactionService.saveJobExecution(job, NOW, NOW, JobStatus.FAILURE, any()) }
  }

  @Test
  fun `should record a failed run when it stops after an answered holding`() {
    queue(answering("Õnne AS"), failing("Põhja Pank"), failing("Šveitsi Pank"), failing("Ühispank"))
    job.collectMissingLogos()
    verify { jobTransactionService.saveJobExecution(job, NOW, NOW, JobStatus.FAILURE, any()) }
  }

  @Test
  fun `should restart the failure count after an answered holding`() {
    queue(
      failing("Põhja Pank"),
      failing("Šveitsi Pank"),
      answering("Õnne AS"),
      failing("Ühispank"),
      failing("Žalgiris AB"),
      answering("Läti Pank"),
    )
    job.collectMissingLogos()
    verify(exactly = 6) { logoFallbackService.fetchLogo(any(), any(), any()) }
    verify { jobTransactionService.saveJobExecution(job, NOW, NOW, JobStatus.SUCCESS, any()) }
  }

  @Test
  fun `should neither count nor reset the failure count for a skipped holding`() {
    queue(
      failing("Põhja Pank"),
      skipped("Tundmatu OÜ"),
      failing("Šveitsi Pank"),
      skipped("Nimetu AS"),
      failing("Ühispank"),
      answering("Õnne AS"),
    )
    job.collectMissingLogos()
    verify(exactly = 0) { logoFallbackService.fetchLogo("Õnne AS", any(), any()) }
    verify { jobTransactionService.saveJobExecution(job, NOW, NOW, JobStatus.FAILURE, any()) }
  }

  @Test
  fun `should record a failed run when every tried holding failed`() {
    queue(failing("Põhja Pank"), failing("Šveitsi Pank"))
    job.collectMissingLogos()
    verify { jobTransactionService.saveJobExecution(job, NOW, NOW, JobStatus.FAILURE, any()) }
  }

  @Test
  fun `should record a failed run when most of the queue is skipped and the rest failed`() {
    queue(skipped("Tundmatu OÜ"), failing("Põhja Pank"), skipped("Nimetu AS"), failing("Šveitsi Pank"), skipped("Õnne AS"))
    job.collectMissingLogos()
    verify { jobTransactionService.saveJobExecution(job, NOW, NOW, JobStatus.FAILURE, any()) }
  }

  @Test
  fun `should record a successful run when some tried holdings answered`() {
    queue(failing("Põhja Pank"), answering("Õnne AS"))
    job.collectMissingLogos()
    verify { jobTransactionService.saveJobExecution(job, NOW, NOW, JobStatus.SUCCESS, any()) }
  }

  @Test
  fun `should record a successful run when every holding is skipped`() {
    queue(skipped("Tundmatu OÜ"), skipped("Nimetu AS"))
    job.collectMissingLogos()
    verify { jobTransactionService.saveJobExecution(job, NOW, NOW, JobStatus.SUCCESS, any()) }
  }

  @Test
  fun `should keep the last error in the job execution row`() {
    val message = slot<String?>()
    every { jobTransactionService.saveJobExecution(any(), any(), any(), any(), captureNullable(message)) } returns mockk()
    queue(failing("Põhja Pank", LogoSelectionFailedException("Vision API returned a blank reply for Põhja Pank")))
    job.collectMissingLogos()
    expect(message.captured).notToEqualNull().toContain("Vision API returned a blank reply for Põhja Pank")
  }

  @Test
  fun `should record a failed run when the holding query fails`() {
    val message = slot<String?>()
    every { jobTransactionService.saveJobExecution(any(), any(), any(), any(), captureNullable(message)) } returns mockk()
    every { etfHoldingRepository.findHoldingsWithoutLogosForCurrentPortfolio(any(), any()) } throws IllegalStateException("Ühendus katkes")
    job.collectMissingLogos()
    verify { jobTransactionService.saveJobExecution(job, NOW, NOW, JobStatus.FAILURE, any()) }
    expect(message.captured).notToEqualNull().toContain("Ühendus katkes")
  }

  @Test
  fun `should record a run when executed as a job`() {
    queue()
    job.execute()
    verify { jobTransactionService.saveJobExecution(job, NOW, NOW, JobStatus.SUCCESS, any()) }
  }

  private fun queue(vararg holdings: EtfHolding) {
    every { etfHoldingRepository.findHoldingsWithoutLogosForCurrentPortfolio(any(), any()) } returns holdings.toList()
    holdings.forEachIndexed { index, holding ->
      holding.id = index + 1L
      every { etfHoldingRepository.findById(holding.id) } returns Optional.of(holding)
    }
  }

  private fun failing(
    name: String,
    error: Exception = LogoSearchUnavailableException("blocked"),
  ): EtfHolding {
    every { logoFallbackService.fetchLogo(name, null, null) } throws error
    return createHolding(name = name, countryCode = "EE")
  }

  private fun answering(name: String): EtfHolding {
    every { logoFallbackService.fetchLogo(name, null, null) } returns null
    return createHolding(name = name, countryCode = "EE")
  }

  private fun finding(name: String): EtfHolding {
    every { logoFallbackService.fetchLogo(name, null, null) } returns
      LogoFetchResult(imageData = byteArrayOf(0x89.toByte()), source = LogoSource.NVSTLY_ICONS)
    return createHolding(name = name, countryCode = "EE")
  }

  private fun skipped(name: String): EtfHolding = createHolding(name = name)

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

  companion object {
    private val NOW = Instant.parse("2026-10-30T12:00:00Z")
  }
}
