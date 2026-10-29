package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LogoFallbackService(
  private val imageSearchLogoService: ImageSearchLogoService,
  private val logoValidationService: LogoValidationService,
  private val imageDownloadService: ImageDownloadService,
  private val openRouterLogoSelectionService: OpenRouterLogoSelectionService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun fetchLogo(
    companyName: String,
    existingTicker: String?,
    lightyearLogoUrl: String?,
  ): LogoFetchResult? =
    tryLightyearLogo(lightyearLogoUrl)
      ?: tryTickerLogo(existingTicker)
      ?: tryLlmSelectedLogo(companyName, existingTicker)?.copy(ticker = existingTicker)
      ?: run {
        log.debug("All logo sources exhausted for company: $companyName")
        null
      }

  private fun tryLightyearLogo(logoUrl: String?): LogoFetchResult? {
    if (logoUrl.isNullOrBlank()) return null
    log.debug("Trying Lightyear logo URL: $logoUrl")
    val imageData =
      runCatching { imageDownloadService.download(logoUrl) }
        .onFailure { log.debug("Failed to download Lightyear logo: ${it.message}") }
        .getOrNull() ?: return null
    if (!logoValidationService.isValidLogo(imageData)) {
      log.debug("Lightyear logo failed validation")
      return null
    }
    log.info("Successfully fetched logo from Lightyear")
    return LogoFetchResult(imageData = imageData, source = LogoSource.LIGHTYEAR)
  }

  private fun tryTickerLogo(ticker: String?): LogoFetchResult? {
    if (ticker.isNullOrBlank()) return null
    return TickerLogo.entries.firstNotNullOfOrNull { tryTickerLogo(it, ticker) }
  }

  private fun tryTickerLogo(
    logo: TickerLogo,
    ticker: String,
  ): LogoFetchResult? {
    log.debug("Trying ${logo.source} for ticker: $ticker")
    val imageData = imageDownloadService.downloadOrNull(logo.url(ticker)) ?: return null
    if (!logoValidationService.isValidLogo(imageData)) {
      log.debug("${logo.source} logo failed validation for ticker: $ticker")
      return null
    }
    log.info("Successfully fetched logo from ${logo.source} for ticker: $ticker")
    return LogoFetchResult(imageData = imageData, source = logo.source, ticker = ticker)
  }

  private fun tryLlmSelectedLogo(
    companyName: String,
    ticker: String?,
  ): LogoFetchResult? {
    if (companyName.isBlank()) return null
    log.debug("Trying LLM-selected logo for company: $companyName")
    val searchQuery = LogoSearchQueryBuilder.buildQuery(companyName, ticker)
    val candidates = imageSearchLogoService.searchLogoCandidates(searchQuery)
    if (candidates.isEmpty()) {
      log.debug("No logo candidates found for company: $companyName")
      return null
    }
    val selectionResult = openRouterLogoSelectionService.selectBestLogo(companyName, ticker, candidates)
    if (selectionResult == null) {
      log.debug("LLM selection failed for company: $companyName")
      return null
    }
    log.info("Successfully fetched logo from ${selectionResult.source} for company: $companyName")
    return LogoFetchResult(imageData = selectionResult.imageData, source = selectionResult.source)
  }
}
