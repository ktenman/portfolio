package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.logo.LogoCacheService
import ee.tenman.portfolio.service.logo.LogoFallbackService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val DEFAULT_INITIAL_DELAY = "3600000"
private const val DEFAULT_FIXED_DELAY = "14400000"

@Component
@ScheduledJob
class EtfLogoCollectionJob(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val logoFallbackService: LogoFallbackService,
  private val logoCacheService: LogoCacheService,
  private val imageProcessingService: ImageProcessingService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(
    initialDelayString = "\${scheduling.jobs.logo-collection.initial-delay:$DEFAULT_INITIAL_DELAY}",
    fixedDelayString = "\${scheduling.jobs.logo-collection.fixed-delay:$DEFAULT_FIXED_DELAY}",
  )
  fun collectMissingLogos() {
    log.info("Starting ETF logo collection job")
    val holdings = etfHoldingRepository
      .findHoldingsWithoutLogosForCurrentPortfolio()
      .filter { it.logoSource == null && !it.countryCode.isNullOrBlank() }
    log.info("Found ${holdings.size} holdings without logos")
    holdings.forEach { processHolding(it) }
    log.info("Completed ETF logo collection job")
  }

  fun processHolding(holding: EtfHolding) {
    runCatching { processHoldingInternal(holding) }
      .onFailure { log.warn("Failed to process holding ${holding.id}: ${it.message}") }
  }

  private fun processHoldingInternal(holding: EtfHolding) {
    val result =
      runCatching { logoFallbackService.fetchLogo(holding.name, holding.ticker, null) }
        .onFailure { log.warn("Logo fetch failed for ${holding.name}: ${it.message}") }
        .getOrNull() ?: return
    val processedImage = imageProcessingService.resizeToMaxDimension(result.imageData)
    logoCacheService.saveLogo(holding.uuid, processedImage)
    log.info("Saved logo from ${result.source} for: ${holding.name}")
    holding.logoSource = result.source
    etfHoldingRepository.save(holding)
  }
}
