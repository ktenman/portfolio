package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.DIVERSIFICATION_ETFS_CACHE
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.logo.LogoCacheService
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class EtfHoldingService(
  private val etfHoldingPersistenceService: EtfHoldingPersistenceService,
  private val holdingIdentityService: HoldingIdentityService,
  private val logoCacheService: LogoCacheService,
  private val imageDownloadService: ImageDownloadService,
  private val imageProcessingService: ImageProcessingService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Cacheable("etf:holdings", key = "#etfSymbol + ':' + #date")
  fun hasHoldingsForDate(
    etfSymbol: String,
    date: LocalDate,
  ): Boolean = etfHoldingPersistenceService.hasHoldingsForDate(etfSymbol, date)

  @Caching(
    evict = [
      CacheEvict(value = ["etf:holdings"], key = "#etfSymbol + ':' + #date"),
      CacheEvict(value = [DIVERSIFICATION_ETFS_CACHE], allEntries = true),
    ],
  )
  fun saveHoldings(
    etfSymbol: String,
    date: LocalDate,
    holdings: List<HoldingData>,
  ) {
    val reuseHints = resolveReuseHints(holdings)
    val savedHoldings = etfHoldingPersistenceService.saveHoldings(etfSymbol, date, holdings, reuseHints)
    holdings.forEach { holdingData ->
      val holding = savedHoldings[holdingData.name] ?: return@forEach
      downloadLightyearLogo(holding, holdingData.logoUrl)
    }
  }

  private fun resolveReuseHints(holdings: List<HoldingData>): Map<Int, Long> =
    holdings
      .withIndex()
      .mapNotNull { (index, holdingData) -> resolveMatchingHoldingId(holdingData)?.let { index to it } }
      .toMap()

  private fun resolveMatchingHoldingId(holdingData: HoldingData): Long? {
    val candidates = collectCandidates(holdingData)
    if (candidates.any { it.name.equals(holdingData.name, ignoreCase = true) }) return null
    return candidates
      .firstOrNull { holdingIdentityService.isSameCompany(it.name, holdingData.name, holdingData.ticker) == true }
      ?.id
  }

  private fun collectCandidates(holdingData: HoldingData): List<EtfHolding> {
    val byTicker =
      holdingData.ticker
        ?.takeIf { it.isNotBlank() }
        ?.let { etfHoldingPersistenceService.findByTicker(it) }
        ?: emptyList()
    val byBlockKey = etfHoldingPersistenceService.findByNameBlockKey(holdingData.name)
    return (byTicker + byBlockKey).distinctBy { it.id }.sortedBy { it.id }
  }

  fun findOrCreateHolding(
    name: String,
    ticker: String?,
    sector: String? = null,
  ): EtfHolding = etfHoldingPersistenceService.findOrCreateHolding(name, ticker, sector)

  private fun downloadLightyearLogo(
    holding: EtfHolding,
    lightyearLogoUrl: String?,
  ) {
    if (lightyearLogoUrl.isNullOrBlank()) return
    if (holding.logoSource == LogoSource.LIGHTYEAR) return
    val imageData =
      runCatching { imageDownloadService.download(lightyearLogoUrl) }
        .onFailure { log.debug("Failed to download Lightyear logo for ${holding.name}: ${it.message}") }
        .getOrNull() ?: return
    val processedImage = imageProcessingService.resizeToMaxDimension(imageData)
    logoCacheService.saveLogo(holding.uuid, processedImage)
    log.info("Saved Lightyear logo for: ${holding.name}")
    holding.logoSource = LogoSource.LIGHTYEAR
    etfHoldingPersistenceService.saveHolding(holding)
  }
}
