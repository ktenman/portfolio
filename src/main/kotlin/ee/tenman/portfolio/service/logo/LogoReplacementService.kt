package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.configuration.LogoReplacementProperties
import ee.tenman.portfolio.dto.LogoCandidateDto
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Base64
import java.util.UUID

@Service
class LogoReplacementService(
  private val logoOwnerService: LogoOwnerService,
  private val imageSearchLogoService: ImageSearchLogoService,
  private val imageDownloadService: ImageDownloadService,
  private val logoValidationService: LogoValidationService,
  private val imageProcessingService: ImageProcessingService,
  private val logoCandidateCacheService: LogoCandidateCacheService,
  private val properties: LogoReplacementProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Suppress("ReturnCount")
  fun getCandidates(holdingUuid: UUID): List<LogoCandidateDto> {
    val cachedData = logoCandidateCacheService.getCachedData(holdingUuid)
    if (cachedData != null) {
      log.debug("Returning cached candidates for holding UUID: $holdingUuid")
      return cachedToDtos(cachedData)
    }
    val searchQuery = logoOwnerService.searchQuery(holdingUuid)
    if (searchQuery == null) {
      log.warn("No holding or instrument found for UUID: $holdingUuid")
      return emptyList()
    }
    val candidates = candidates(logoOwnerService.ticker(holdingUuid), searchQuery)
    if (candidates.isEmpty()) {
      log.debug("No candidates found for: $searchQuery")
      return emptyList()
    }
    val allValidated = validateCandidates(candidates)
    if (allValidated.isEmpty()) {
      log.warn("No valid candidates found for: $searchQuery")
      return emptyList()
    }
    val validatedCandidates = allValidated.take(properties.maxDisplayCandidates)
    val validCandidates = validatedCandidates.map { it.first }
    val imageData = validatedCandidates.associate { it.first.index to it.second }
    logoCandidateCacheService.cacheValidatedCandidates(holdingUuid, validCandidates, imageData)
    log.info("Found ${validatedCandidates.size} valid candidates for: $searchQuery")
    return toDtos(validatedCandidates)
  }

  private fun candidates(
    ticker: String?,
    searchQuery: String,
  ): List<LogoCandidate> =
    (tickerCandidates(ticker) + imageSearchLogoService.searchLogoCandidates(searchQuery, properties.maxSearchResults))
      .mapIndexed { index, candidate -> candidate.copy(index = index) }

  private fun tickerCandidates(ticker: String?): List<LogoCandidate> {
    if (ticker.isNullOrBlank()) return emptyList()
    return TickerLogo.entries.map { LogoCandidate(imageUrl = it.url(ticker), thumbnailUrl = it.url(ticker), title = ticker, index = 0) }
  }

  private fun cachedToDtos(cachedData: CachedLogoData): List<LogoCandidateDto> =
    cachedData.candidates.mapNotNull { candidate ->
      val imageBytes = cachedData.images[candidate.index] ?: return@mapNotNull null
      val mediaType = logoValidationService.detectMediaType(imageBytes)
      val base64 = Base64.getEncoder().encodeToString(imageBytes)
      LogoCandidateDto(
        thumbnailUrl = candidate.thumbnailUrl,
        title = candidate.title,
        index = candidate.index,
        imageDataUrl = "data:$mediaType;base64,$base64",
      )
    }

  private fun toDtos(validatedCandidates: List<Pair<LogoCandidate, ByteArray>>): List<LogoCandidateDto> =
    validatedCandidates.map { (candidate, imageBytes) ->
      val mediaType = logoValidationService.detectMediaType(imageBytes)
      val base64 = Base64.getEncoder().encodeToString(imageBytes)
      LogoCandidateDto(
        thumbnailUrl = candidate.thumbnailUrl,
        title = candidate.title,
        index = candidate.index,
        imageDataUrl = "data:$mediaType;base64,$base64",
      )
    }

  private fun validateCandidates(candidates: List<LogoCandidate>): List<Pair<LogoCandidate, ByteArray>> =
    runBlocking(Dispatchers.IO.limitedParallelism(properties.parallelValidationThreads)) {
      candidates
        .map { candidate ->
          async {
            withTimeoutOrNull(properties.downloadTimeoutMs) {
              val imageData = downloadImage(candidate.imageUrl) ?: downloadImage(candidate.thumbnailUrl)
              if (imageData == null) {
                log.debug("Failed to download candidate ${candidate.index}")
                return@withTimeoutOrNull null
              }
              if (!logoValidationService.isValidLogo(imageData)) {
                log.debug("Validation failed for candidate ${candidate.index}")
                return@withTimeoutOrNull null
              }
              candidate to imageData
            }
          }
        }.awaitAll()
        .filterNotNull()
    }

  fun replaceLogo(
    holdingUuid: UUID,
    candidateIndex: Int,
  ): Boolean {
    val cachedData = logoCandidateCacheService.getCachedData(holdingUuid)
    val candidate = findCandidate(cachedData, holdingUuid, candidateIndex) ?: return false
    val cachedImage = cachedData?.images?.get(candidateIndex)
    val processedImage =
      if (cachedImage != null) {
        imageProcessingService.resizeToMaxDimension(cachedImage)
      } else {
        downloadAndProcessImage(candidate) ?: return false
      }
    return saveLogo(holdingUuid, processedImage)
  }

  private fun findCandidate(
    cachedData: CachedLogoData?,
    holdingUuid: UUID,
    candidateIndex: Int,
  ): LogoCandidate? {
    val candidates = cachedData?.candidates
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

  fun uploadLogo(
    uuid: UUID,
    image: ByteArray,
  ): Boolean {
    require(logoValidationService.isValidLogo(image)) { "Logo must be a square PNG, JPEG or WebP image of at least 32 px" }
    return saveLogo(uuid, imageProcessingService.resizeToMaxDimension(image))
  }

  private fun downloadImage(url: String): ByteArray? = imageDownloadService.downloadOrNull(url)

  private fun saveLogo(
    uuid: UUID,
    processedImage: ByteArray,
  ): Boolean {
    if (!logoOwnerService.saveManualLogo(uuid, processedImage)) return false
    logoCandidateCacheService.clearCache(uuid)
    return true
  }

  @Suppress("ReturnCount")
  fun searchByName(name: String): List<LogoCandidateDto> {
    val cachedData = logoCandidateCacheService.getCachedDataByName(name)
    if (cachedData != null) {
      log.debug("Returning cached candidates for name: $name")
      return cachedToDtos(cachedData)
    }
    val searchQuery = LogoSearchQueryBuilder.buildQuery(name, null)
    val candidates = imageSearchLogoService.searchLogoCandidates(searchQuery, properties.maxSearchResults)
    if (candidates.isEmpty()) {
      log.debug("No candidates found for name: $name")
      return emptyList()
    }
    val allValidated = validateCandidates(candidates)
    if (allValidated.isEmpty()) {
      log.warn("No valid candidates found for name: $name")
      return emptyList()
    }
    val validatedCandidates = allValidated.take(properties.maxDisplayCandidates)
    val validCandidates = validatedCandidates.map { it.first }
    val imageData = validatedCandidates.associate { it.first.index to it.second }
    logoCandidateCacheService.cacheByName(name, validCandidates, imageData)
    log.info("Found ${validatedCandidates.size} valid candidates for name: $name")
    return toDtos(validatedCandidates)
  }
}
