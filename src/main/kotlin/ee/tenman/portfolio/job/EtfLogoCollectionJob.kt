package ee.tenman.portfolio.job

import ee.tenman.portfolio.common.orNull
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.exception.LogoSelectionFailedException
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.service.etf.EtfHoldingPersistenceService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.JobTransactionService
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.service.logo.LogoFallbackService
import ee.tenman.portfolio.service.logo.LogoFetchResult
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

private const val DEFAULT_INITIAL_DELAY = "3600000"
private const val DEFAULT_FIXED_DELAY = "14400000"
private const val US_COUNTRY_CODE = "US"

@Component
@ScheduledJob
class EtfLogoCollectionJob(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val logoFallbackService: LogoFallbackService,
  private val minioService: MinioService,
  private val imageProcessingService: ImageProcessingService,
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService,
  private val jobTransactionService: JobTransactionService,
  private val clock: Clock,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val MAX_ATTEMPTS = 3
    private val RETRY_AFTER = Duration.ofDays(90)
  }

  @Scheduled(
    initialDelayString = "\${scheduling.jobs.logo-collection.initial-delay:$DEFAULT_INITIAL_DELAY}",
    fixedDelayString = "\${scheduling.jobs.logo-collection.fixed-delay:$DEFAULT_FIXED_DELAY}",
  )
  fun collectMissingLogos() {
    log.info("Starting ETF logo collection job")
    val startTime = Instant.now(clock)
    val result = runCatching { processQueue() }.onFailure { log.error("ETF logo collection job crashed", it) }
    jobTransactionService.saveJobExecution(
      job = this,
      startTime = startTime,
      endTime = Instant.now(clock),
      status = if (result.getOrNull()?.broken == false) JobStatus.SUCCESS else JobStatus.FAILURE,
      message = result.fold({ it.message }, { "Logo collection crashed: ${it.message}" }),
    )
    log.info("Completed ETF logo collection job")
  }

  override fun execute() {
    collectMissingLogos()
  }

  fun processHolding(holdingId: Long): LogoOutcome =
    runCatching { processHoldingInternal(holdingId) }
      .onFailure { log.warn("Failed to process holding $holdingId: ${it.message}") }
      .getOrElse { LogoOutcome.Failed("Holding $holdingId: ${it.message}") }

  private fun processQueue(): LogoRun {
    val retryBefore = Instant.now(clock).minus(RETRY_AFTER)
    val holdingIds = etfHoldingRepository.findHoldingsWithoutLogosForCurrentPortfolio(MAX_ATTEMPTS, retryBefore).map { it.id }
    log.info("Found ${holdingIds.size} holdings without logos")
    return holdingIds.fold(LogoRun()) { run, id -> if (run.stopped) run else run.after(processHolding(id)) }
  }

  private fun processHoldingInternal(holdingId: Long): LogoOutcome {
    val holding = etfHoldingRepository.findById(holdingId).orNull() ?: return LogoOutcome.Skipped
    if (holding.logoSource != null) return LogoOutcome.Skipped
    if (holding.countryCode.isNullOrBlank()) {
      log.info("Skipping logo fetch for ${holding.name}: no country code")
      return LogoOutcome.Skipped
    }
    val result =
      runCatching { logoFallbackService.fetchLogo(holding.name, usTicker(holding), null) }.getOrElse {
        log.warn("Logo fetch failed for ${holding.name}: ${it.message}")
        if (it is LogoSelectionFailedException) useAttempt(holding)
        return LogoOutcome.Failed("${holding.name}: ${it.message}")
      } ?: return noLogo(holding)
    return storeLogo(holding, result)
  }

  private fun storeLogo(
    holding: EtfHolding,
    result: LogoFetchResult,
  ): LogoOutcome {
    val processedImage =
      runCatching { imageProcessingService.resizeToMaxDimension(result.imageData) }.getOrElse {
        log.warn("Chosen logo cannot be decoded for ${holding.name}: ${it.message}")
        return noLogo(holding)
      }
    minioService.uploadLogo(holding.uuid, processedImage)
    log.info("Saved logo from ${result.source} for: ${holding.name}")
    holding.logoSource = result.source
    etfHoldingRepository.save(holding)
    return LogoOutcome.Answered
  }

  private fun noLogo(holding: EtfHolding): LogoOutcome {
    useAttempt(holding)
    return LogoOutcome.Answered
  }

  private fun useAttempt(holding: EtfHolding) {
    etfHoldingPersistenceService.incrementLogoFetchAttempts(holding.id, Instant.now(clock))
  }

  private fun usTicker(holding: EtfHolding): String? = holding.ticker.takeIf { holding.countryCode == US_COUNTRY_CODE }
}
