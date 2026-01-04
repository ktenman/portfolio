package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterVisionClient
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest.ImageContent
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest.Message
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest.TextContent
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Base64

@Service
class OpenRouterLogoSelectionService(
  private val openRouterVisionClient: OpenRouterVisionClient,
  private val openRouterProperties: OpenRouterProperties,
  private val imageDownloadService: ImageDownloadService,
  private val logoValidationService: LogoValidationService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun selectBestLogo(
    companyName: String,
    ticker: String?,
    candidates: List<LogoCandidate>,
  ): LogoSelectionResult? {
    if (candidates.isEmpty()) return null
    val fastPathResult = tryFastPath(companyName, ticker, candidates)
    if (fastPathResult != null) return fastPathResult
    if (candidates.size == 1) return downloadAndValidate(candidates[0], LogoSource.BING)
    return tryLlmSelection(companyName, candidates) ?: downloadFirstValid(candidates)
  }

  private fun tryFastPath(
    companyName: String,
    ticker: String?,
    candidates: List<LogoCandidate>,
  ): LogoSelectionResult? {
    val normalizedName = companyName.lowercase().trim()
    val normalizedTicker = ticker?.lowercase()?.trim()
    for (candidate in candidates) {
      val titleLower = candidate.title.lowercase()
      val isExactMatch =
        titleLower.contains(normalizedName) ||
        (normalizedTicker != null && titleLower.contains(normalizedTicker))
      if (!isExactMatch) continue
      log.info("Fast path match for $companyName: title '${candidate.title}' matches")
      val result = downloadAndValidate(candidate, LogoSource.BING)
      if (result != null) return result
    }
    return null
  }

  private fun tryLlmSelection(
    companyName: String,
    candidates: List<LogoCandidate>,
  ): LogoSelectionResult? {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key not configured, skipping LLM selection")
      return null
    }
    val downloadedImages = downloadCandidateImages(candidates)
    if (downloadedImages.isEmpty()) return null
    val selectedIndex = callVisionApi(companyName, downloadedImages)
    if (selectedIndex == null || selectedIndex !in downloadedImages.indices) {
      log.debug("LLM selection failed or returned invalid index for $companyName")
      return null
    }
    val (candidate, imageData) = downloadedImages[selectedIndex]
    log.info("LLM selected logo at index $selectedIndex for $companyName")
    return LogoSelectionResult(
      selectedIndex = candidate.index,
      imageData = imageData,
      source = LogoSource.LLM_SELECTED,
    )
  }

  private fun downloadCandidateImages(candidates: List<LogoCandidate>): List<Pair<LogoCandidate, ByteArray>> {
    val maxCandidates = minOf(candidates.size, MAX_LLM_CANDIDATES)
    return candidates.take(maxCandidates).mapNotNull { candidate ->
      val imageData =
        runCatching { imageDownloadService.download(candidate.imageUrl) }
        .onFailure { log.debug("Failed to download ${candidate.imageUrl}: ${it.message}") }
        .getOrNull()
      if (imageData == null || !logoValidationService.isValidLogo(imageData)) return@mapNotNull null
      candidate to imageData
    }
  }

  private fun callVisionApi(
    companyName: String,
    images: List<Pair<LogoCandidate, ByteArray>>,
  ): Int? {
    val contentParts = mutableListOf<OpenRouterVisionRequest.ContentPart>()
    contentParts.add(TextContent(text = buildPrompt(companyName, images.size)))
    images.forEachIndexed { index, (_, imageData) ->
      val base64 = Base64.getEncoder().encodeToString(imageData)
      val mediaType = logoValidationService.detectMediaType(imageData)
      contentParts.add(
        ImageContent(imageUrl = ImageContent.ImageUrl(url = "data:$mediaType;base64,$base64")),
      )
      contentParts.add(TextContent(text = "Image ${index + 1}"))
    }
    val request =
      OpenRouterVisionRequest(
      model = openRouterProperties.visionModel,
      messages = listOf(Message(role = "user", content = contentParts)),
      maxTokens = 20,
      temperature = 0.0,
    )
    return runBlocking {
      withTimeoutOrNull(openRouterProperties.apiTimeoutMs) {
        runCatching {
          val response =
            openRouterVisionClient.chatCompletion(
            "Bearer ${openRouterProperties.apiKey}",
            request,
          )
          parseSelectionResponse(response.extractContent(), images.size)
        }.onFailure { log.warn("Vision API call failed: ${it.message}") }.getOrNull()
      } ?: run {
        log.warn("Vision API call timed out after ${openRouterProperties.apiTimeoutMs}ms for $companyName")
        null
      }
    }
  }

  private fun buildPrompt(
    companyName: String,
    imageCount: Int,
  ): String =
    "Which image (1-$imageCount) is the best company logo for '$companyName'? " +
      "Reply with only the number."

  private fun parseSelectionResponse(
    response: String?,
    maxIndex: Int,
  ): Int? {
    if (response.isNullOrBlank()) return null
    val number = response.trim().filter { it.isDigit() }.toIntOrNull() ?: return null
    if (number < 1 || number > maxIndex) return null
    return number - 1
  }

  private fun downloadFirstValid(candidates: List<LogoCandidate>): LogoSelectionResult? {
    for (candidate in candidates) {
      val result = downloadAndValidate(candidate, LogoSource.BING)
      if (result != null) return result
    }
    return null
  }

  private fun downloadAndValidate(
    candidate: LogoCandidate,
    source: LogoSource,
  ): LogoSelectionResult? {
    val imageData =
      runCatching { imageDownloadService.download(candidate.imageUrl) }
      .onFailure { log.debug("Failed to download ${candidate.imageUrl}: ${it.message}") }
      .getOrNull() ?: return null
    if (!logoValidationService.isValidLogo(imageData)) {
      log.debug("Logo validation failed for ${candidate.imageUrl}")
      return null
    }
    return LogoSelectionResult(
      selectedIndex = candidate.index,
      imageData = imageData,
      source = source,
    )
  }

  companion object {
    private const val MAX_LLM_CANDIDATES = 5
  }
}
