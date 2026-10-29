package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.InstrumentCategory
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.lightyear.LightyearPriceService
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.service.instrument.InstrumentService
import ee.tenman.portfolio.service.logo.LogoFallbackService
import ee.tenman.portfolio.service.logo.LogoFetchResult
import ee.tenman.portfolio.service.logo.NvstlyLogoService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val DEFAULT_INITIAL_DELAY = "60000"
private const val DEFAULT_FIXED_DELAY = "14400000"
private const val BINANCE_QUOTE_SUFFIX = "EUR"

@Component
@ScheduledJob
class InstrumentLogoCollectionJob(
  private val instrumentRepository: InstrumentRepository,
  private val instrumentService: InstrumentService,
  private val lightyearPriceService: LightyearPriceService,
  private val nvstlyLogoService: NvstlyLogoService,
  private val logoFallbackService: LogoFallbackService,
  private val imageProcessingService: ImageProcessingService,
  private val minioService: MinioService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(
    initialDelayString = "\${scheduling.jobs.logo-collection.initial-delay:$DEFAULT_INITIAL_DELAY}",
    fixedDelayString = "\${scheduling.jobs.logo-collection.fixed-delay:$DEFAULT_FIXED_DELAY}",
  )
  fun collectMissingLogos() {
    val instruments = instrumentRepository.findByLogoSourceIsNull().filterNot { it.isCash() }
    log.info("Found ${instruments.size} instruments without logos")
    val logoUrls = lightyearLogoUrls(instruments.filter { it.isEtf() })
    if (logoUrls == null) log.warn("Leaving ETF logos for the next run because the Lightyear lookup failed")
    instruments
      .filter { logoUrls != null || !it.isEtf() }
      .forEach { instrument ->
        runCatching { collect(instrument, logoUrls?.get(instrument.symbol)) }
          .onFailure { log.warn("Failed to collect the logo of ${instrument.symbol}: ${it.message}") }
      }
  }

  private fun lightyearLogoUrls(etfs: List<Instrument>): Map<String, String>? =
    runCatching { lightyearPriceService.fetchLogoUrls(etfs.map { it.symbol }) }
      .onFailure { log.warn("Lightyear logo lookup failed for ${etfs.size} instruments: ${it.message}") }
      .getOrNull()

  private fun Instrument.isEtf(): Boolean = category == InstrumentCategory.ETF.name

  private fun collect(
    instrument: Instrument,
    logoUrl: String?,
  ) {
    val result = cryptoLogo(instrument) ?: logoFallbackService.fetchLogo(instrument.issuer(), null, logoUrl) ?: return
    minioService.uploadLogo(instrument.uuid, imageProcessingService.resizeToMaxDimension(result.imageData))
    instrumentService.updateLogoSource(instrument.id, instrument.symbol, result.source)
    log.info("Saved logo from ${result.source} for ${instrument.symbol}")
  }

  private fun cryptoLogo(instrument: Instrument): LogoFetchResult? {
    if (instrument.providerName != ProviderName.BINANCE) return null
    return nvstlyLogoService
      .fetchCryptoLogo(instrument.symbol.removeSuffix(BINANCE_QUOTE_SUFFIX))
      ?.let { LogoFetchResult(imageData = it, source = LogoSource.NVSTLY_ICONS) }
  }
}
