package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.DIVERSIFICATION_ETFS_CACHE
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.domain.VanguardCountryUpdate
import ee.tenman.portfolio.domain.VanguardHoldingUpdates
import ee.tenman.portfolio.domain.VanguardIndustryUpdate
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.vanguard.VanguardFundSnapshot
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
  private val minioService: MinioService,
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

  fun resolveVanguardUpdates(snapshot: VanguardFundSnapshot): VanguardHoldingUpdates {
    val industries = mutableListOf<VanguardIndustryUpdate>()
    val countries = mutableListOf<VanguardCountryUpdate>()
    snapshot.holdings.forEach { data ->
      val codes = snapshot.countryCodes[data.name].orEmpty()
      if (data.industry == null && codes.isEmpty()) return@forEach
      val holding = resolveVanguardHolding(data) ?: return@forEach
      data.industry?.let { industries += VanguardIndustryUpdate(holding.uuid, it, snapshot.effectiveDate) }
      countries += codes.map { VanguardCountryUpdate(holding.uuid, it, snapshot.effectiveDate) }
    }
    return VanguardHoldingUpdates(industries, countries)
  }

  private fun resolveVanguardHolding(data: HoldingData): EtfHolding? {
    val candidates = collectCandidates(data)
    val exact = candidates.filter { it.name.equals(data.name, ignoreCase = true) }
    if (exact.isNotEmpty()) return exact.singleOrNull()
    return candidates.filter { holdingIdentityService.isSameCompany(it.name, data.name, data.ticker) == true }.singleOrNull()
  }

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
    minioService.uploadLogo(holding.uuid, processedImage)
    log.info("Saved Lightyear logo for: ${holding.name}")
    holding.logoSource = LogoSource.LIGHTYEAR
    etfHoldingPersistenceService.saveHolding(holding)
  }
}
