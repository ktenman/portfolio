package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.dto.LogoCandidateDto
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class LogoReplacementService(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val imageSearchLogoService: ImageSearchLogoService,
  private val imageDownloadService: ImageDownloadService,
  private val logoValidationService: LogoValidationService,
  private val imageProcessingService: ImageProcessingService,
  private val minioService: MinioService,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val candidateCache = ConcurrentHashMap<UUID, List<LogoCandidate>>()

  fun getCandidates(holdingUuid: UUID): List<LogoCandidateDto> {
    val holding = etfHoldingRepository.findAll().find { it.uuid == holdingUuid }
    if (holding == null) {
      log.warn("Holding not found for UUID: $holdingUuid")
      return emptyList()
    }
    val searchQuery = buildSearchQuery(holding.name, holding.ticker)
    val candidates = imageSearchLogoService.searchLogoCandidates(searchQuery, MAX_CANDIDATES)
    if (candidates.isEmpty()) {
      log.debug("No candidates found for holding: ${holding.name}")
      return emptyList()
    }
    candidateCache[holdingUuid] = candidates
    return candidates.map { LogoCandidateDto(thumbnailUrl = it.thumbnailUrl, title = it.title, index = it.index) }
  }

  @Transactional
  @CacheEvict(value = ["logos"], key = "'uuid-' + #holdingUuid.toString()")
  fun replaceLogo(
    holdingUuid: UUID,
    candidateIndex: Int,
  ): Boolean {
    val candidate = findCandidate(holdingUuid, candidateIndex) ?: return false
    val processedImage = downloadAndProcessImage(candidate) ?: return false
    return uploadAndSave(holdingUuid, processedImage)
  }

  private fun findCandidate(
    holdingUuid: UUID,
    candidateIndex: Int,
  ): LogoCandidate? {
    val candidates = candidateCache[holdingUuid]
    if (candidates.isNullOrEmpty()) {
      log.warn("No cached candidates for holding UUID: $holdingUuid")
      return null
    }
    val candidate = candidates.find { it.index == candidateIndex }
    if (candidate == null) {
      log.warn("Invalid candidate index $candidateIndex for holding UUID: $holdingUuid")
    }
    return candidate
  }

  private fun downloadAndProcessImage(candidate: LogoCandidate): ByteArray? {
    val imageData = downloadImage(candidate.imageUrl) ?: downloadImage(candidate.thumbnailUrl)
    if (imageData == null) {
      log.warn("Failed to download logo from both image and thumbnail URLs for index ${candidate.index}")
      return null
    }
    if (!logoValidationService.isValidLogo(imageData)) {
      log.warn("Logo validation failed for candidate index ${candidate.index}")
      return null
    }
    return imageProcessingService.resizeToMaxDimension(imageData)
  }

  private fun downloadImage(url: String): ByteArray? =
    runCatching { imageDownloadService.download(url) }
      .onFailure { log.debug("Failed to download from $url: ${it.message}") }
      .getOrNull()

  private fun uploadAndSave(
    holdingUuid: UUID,
    processedImage: ByteArray,
  ): Boolean {
    val uploadResult = runCatching { minioService.uploadLogo(holdingUuid, processedImage) }
    if (uploadResult.isFailure) {
      log.error("Failed to upload logo for holding UUID $holdingUuid: ${uploadResult.exceptionOrNull()?.message}")
      return false
    }
    val holding = etfHoldingRepository.findAll().find { it.uuid == holdingUuid }
    if (holding == null) {
      log.warn("Holding not found for UUID: $holdingUuid")
      return false
    }
    holding.logoSource = LogoSource.MANUAL
    etfHoldingRepository.save(holding)
    candidateCache.remove(holdingUuid)
    log.info("Successfully replaced logo for holding: ${holding.name}")
    return true
  }

  private fun buildSearchQuery(
    name: String,
    ticker: String?,
  ): String = if (ticker.isNullOrBlank()) name else "$name $ticker"

  companion object {
    private const val MAX_CANDIDATES = 30
  }
}
