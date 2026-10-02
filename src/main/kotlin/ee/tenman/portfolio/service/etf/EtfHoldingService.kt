package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.configuration.RedisConfiguration.Companion.DIVERSIFICATION_ETFS_CACHE
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.domain.VanguardCountryUpdate
import ee.tenman.portfolio.domain.VanguardHoldingUpdates
import ee.tenman.portfolio.domain.VanguardIndustryUpdate
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.dto.IdentityPair
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
  private val holdingWriteLock: HoldingWriteLock,
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
    val savedHoldings = holdingWriteLock.exclusively { etfHoldingPersistenceService.saveHoldings(etfSymbol, date, holdings, reuseHints) }
    holdings.forEach { holdingData ->
      val holding = savedHoldings[holdingData.name] ?: return@forEach
      downloadLightyearLogo(holding, holdingData.logoUrl)
    }
  }

  private fun resolveReuseHints(holdings: List<HoldingData>): Map<Int, Long> {
    val work =
      holdings.map { holding ->
        holding to
      collectCandidates(holding).takeUnless { exactMatches(it, holding).isNotEmpty() }.orEmpty()
          }
    val answers = holdingIdentityService.resolveAll(identityPairs(work))
    return work
      .mapIndexedNotNull { index, (holding, candidates) ->
        candidates.firstOrNull { answers[pairOf(it, holding)] == true }?.let { index to it.id }
      }.toMap()
  }

  fun resolveVanguardUpdates(snapshot: VanguardFundSnapshot): VanguardHoldingUpdates {
    val work =
      snapshot.holdings
        .filter { it.industry != null || snapshot.countryCodes[it.name].orEmpty().isNotEmpty() }
        .map { it to collectCandidates(it) }
    val answers =
      holdingIdentityService.resolveAll(
      identityPairs(
        work.filter { (data, candidates) ->
      exactMatches(candidates, data).isEmpty()
    },
      ),
    )
    val industries = mutableListOf<VanguardIndustryUpdate>()
    val countries = mutableListOf<VanguardCountryUpdate>()
    work.forEach { (data, candidates) ->
      val holding = resolveVanguardHolding(data, candidates, answers) ?: return@forEach
      data.industry?.let { industries += VanguardIndustryUpdate(holding.uuid, it, snapshot.effectiveDate) }
      countries += snapshot.countryCodes[data.name].orEmpty().map { VanguardCountryUpdate(holding.uuid, it, snapshot.effectiveDate) }
    }
    return VanguardHoldingUpdates(industries, countries)
  }

  private fun resolveVanguardHolding(
    data: HoldingData,
    candidates: List<EtfHolding>,
    answers: Map<IdentityPair, Boolean?>,
  ): EtfHolding? {
    val exact = exactMatches(candidates, data)
    if (exact.isNotEmpty()) return exact.singleOrNull()
    return candidates.filter { answers[pairOf(it, data)] == true }.singleOrNull()
  }

  private fun identityPairs(work: List<Pair<HoldingData, List<EtfHolding>>>): List<IdentityPair> =
    work.flatMap { (data, candidates) -> candidates.map { pairOf(it, data) } }

  private fun pairOf(
    candidate: EtfHolding,
    data: HoldingData,
  ): IdentityPair = IdentityPair(candidate.name, data.name, data.ticker, candidate.countryCode, data.countryCode, candidate.ticker)

  private fun exactMatches(
    candidates: List<EtfHolding>,
    data: HoldingData,
  ): List<EtfHolding> = candidates.filter { it.name.equals(data.name, ignoreCase = true) }

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
