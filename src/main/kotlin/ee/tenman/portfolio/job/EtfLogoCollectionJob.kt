package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.service.logo.LogoFallbackService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ScheduledJob
class EtfLogoCollectionJob(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val logoFallbackService: LogoFallbackService,
  private val minioService: MinioService,
  private val imageProcessingService: ImageProcessingService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 3600000, fixedDelay = 14400000)
  fun collectMissingLogos() {
    log.info("Starting ETF logo collection job")
    val holdingIds = etfHoldingRepository.findHoldingsWithoutLogosForCurrentPortfolio().map { it.id }
    log.info("Found ${holdingIds.size} holdings without logos")
    holdingIds.forEach { processHolding(it) }
    log.info("Completed ETF logo collection job")
  }

  fun processHolding(holdingId: Long) {
    runCatching { processHoldingInternal(holdingId) }
      .onFailure { log.warn("Failed to process holding $holdingId: ${it.message}") }
  }

  private fun processHoldingInternal(holdingId: Long) {
    val holding = etfHoldingRepository.findById(holdingId).orElse(null) ?: return
    if (holding.logoSource != null) return
    val result =
      runCatching { logoFallbackService.fetchLogo(holding.name, holding.ticker, null) }
        .onFailure { log.warn("Logo fetch failed for ${holding.name}: ${it.message}") }
        .getOrNull() ?: return
    updateTickerIfExtracted(holding, result.ticker)
    val processedImage = imageProcessingService.resizeToMaxDimension(result.imageData)
    runCatching { minioService.uploadLogo(holding.id, processedImage) }
      .onSuccess {
        log.info("Uploaded logo from ${result.source} for: ${holding.name}")
        holding.logoSource = result.source
        etfHoldingRepository.save(holding)
      }.onFailure { log.warn("Failed to upload logo for ${holding.name}: ${it.message}") }
  }

  private fun updateTickerIfExtracted(
    holding: EtfHolding,
    extractedTicker: String?,
  ) {
    if (!holding.ticker.isNullOrBlank() || extractedTicker.isNullOrBlank()) return
    log.info("Updating ticker for '${holding.name}': $extractedTicker")
    holding.ticker = extractedTicker
  }
}
