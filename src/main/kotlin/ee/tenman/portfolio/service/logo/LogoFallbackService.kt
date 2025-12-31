package ee.tenman.portfolio.service.logo
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LogoFallbackService(
  private val nvstlyLogoService: NvstlyLogoService,
  private val imageSearchLogoService: ImageSearchLogoService,
  private val logoValidationService: LogoValidationService,
  private val imageDownloadService: ImageDownloadService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun fetchLogo(
    companyName: String,
    existingTicker: String?,
    lightyearLogoUrl: String?,
  ): LogoFetchResult? {
    return tryLightyearLogo(lightyearLogoUrl)
      ?: tryNvstlyLogo(existingTicker)?.copy(ticker = existingTicker)
      ?: tryImageSearchLogo(companyName)?.copy(ticker = existingTicker)
      ?: run {
        log.debug("All logo sources exhausted for company: $companyName")
        null
      }
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

  private fun tryNvstlyLogo(ticker: String?): LogoFetchResult? {
    if (ticker.isNullOrBlank()) return null
    log.debug("Trying nvstly/icons for ticker: $ticker")
    val imageData = nvstlyLogoService.fetchLogo(ticker) ?: return null
    if (!logoValidationService.isValidLogo(imageData)) {
      log.debug("Nvstly logo failed validation for ticker: $ticker")
      return null
    }
    log.info("Successfully fetched logo from nvstly/icons for ticker: $ticker")
    return LogoFetchResult(imageData = imageData, source = LogoSource.NVSTLY_ICONS, ticker = ticker)
  }

  private fun tryImageSearchLogo(companyName: String): LogoFetchResult? {
    if (companyName.isBlank()) return null
    log.debug("Trying image search for company: $companyName")
    val result = imageSearchLogoService.searchAndDownloadLogo(companyName) ?: return null
    if (!logoValidationService.isValidLogo(result.imageData)) {
      log.debug("Image search logo failed validation for company: $companyName")
      return null
    }
    log.info("Successfully fetched logo from ${result.source} for company: $companyName")
    return LogoFetchResult(imageData = result.imageData, source = result.source)
  }
}
