package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.domain.LogoSource
import ee.tenman.portfolio.exception.LogoSelectionFailedException
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
    return tryFastPath(companyName, ticker, candidates) ?: tryLlmSelection(companyName, candidates)
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
      val result = downloadAndValidate(candidate, LogoSource.DUCKDUCKGO)
      if (result != null) return result
    }
    return null
  }

  private fun tryLlmSelection(
    companyName: String,
    candidates: List<LogoCandidate>,
  ): LogoSelectionResult? {
    if (openRouterProperties.apiKey.isBlank()) {
      throw LogoSelectionFailedException("OpenRouter API key is not configured for the logo of $companyName")
    }
    val images = downloadCandidateImages(candidates)
    if (images.isEmpty()) return null
    val answer = callVisionApi(companyName, images)
    if (answer == 0) {
      log.info("LLM rejected every logo candidate for $companyName")
      return null
    }
    log.info("LLM selected image $answer for $companyName")
    return images[answer - 1].second
  }

  private fun downloadCandidateImages(candidates: List<LogoCandidate>): List<Pair<LogoCandidate, LogoSelectionResult>> =
    candidates
      .asSequence()
      .mapNotNull { candidate -> downloadAndValidate(candidate, LogoSource.LLM_SELECTED)?.let { candidate to it } }
      .take(MAX_LLM_CANDIDATES)
      .toList()

  private fun callVisionApi(
    companyName: String,
    images: List<Pair<LogoCandidate, LogoSelectionResult>>,
  ): Int {
    val request = buildVisionRequest(companyName, images)
    val outcome =
      runBlocking {
        withTimeoutOrNull(openRouterProperties.apiTimeoutMs) {
          runCatching { openRouterVisionClient.chatCompletion("Bearer ${openRouterProperties.apiKey}", request) }
        }
      } ?: throw LogoSelectionFailedException(
        "Vision API call timed out after ${openRouterProperties.apiTimeoutMs}ms for $companyName",
      )
    val response =
      outcome.getOrElse { throw LogoSelectionFailedException("Vision API call failed for $companyName: ${it.message}", it) }
    return readAnswer(companyName, response.extractContent(), images.size)
  }

  private fun readAnswer(
    companyName: String,
    reply: String?,
    imageCount: Int,
  ): Int {
    if (reply == null) throw LogoSelectionFailedException("Vision API reply is blank for $companyName")
    return parseSelectionResponse(reply, imageCount)
      ?: throw LogoSelectionFailedException("Vision API reply is unparsable for $companyName: $reply")
  }

  private fun buildVisionRequest(
    companyName: String,
    images: List<Pair<LogoCandidate, LogoSelectionResult>>,
  ): OpenRouterVisionRequest {
    val contentParts = mutableListOf<OpenRouterVisionRequest.ContentPart>()
    contentParts.add(TextContent(text = buildPrompt(companyName, images.size)))
    images.forEachIndexed { index, (candidate, result) ->
      val base64 = Base64.getEncoder().encodeToString(result.imageData)
      val mediaType = logoValidationService.detectMediaType(result.imageData)
      val label = "Image ${index + 1}"
      contentParts.add(
        ImageContent(imageUrl = ImageContent.ImageUrl(url = "data:$mediaType;base64,$base64")),
      )
      contentParts.add(TextContent(text = if (candidate.title.isBlank()) label else "$label: ${candidate.title}"))
    }
    return OpenRouterVisionRequest(
      model = openRouterProperties.visionModel,
      messages = listOf(Message(role = "user", content = contentParts)),
      maxTokens = 200,
      temperature = 0.0,
    )
  }

  private fun buildPrompt(
    companyName: String,
    imageCount: Int,
  ): String =
    "Which image (1-$imageCount) is the company logo of '$companyName'? " +
      "Each image is followed by the title of the page it came from. " +
      "Reply with only the number, or 0 if none of them is this company's logo."

  private fun parseSelectionResponse(
    response: String,
    maxIndex: Int,
  ): Int? =
    Regex("\\d+")
      .find(response)
      ?.value
      ?.toIntOrNull()
      ?.takeIf { it in 0..maxIndex }

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
