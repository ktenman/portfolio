package ee.tenman.portfolio.service.logo

import ee.tenman.portfolio.configuration.BatchLogoValidationProperties
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterVisionClient
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest.ImageContent
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest.Message
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest.TextContent
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.util.Base64
import java.util.UUID

@Service
class BatchLogoValidationService(
  private val openRouterVisionClient: OpenRouterVisionClient,
  private val openRouterProperties: OpenRouterProperties,
  private val batchProperties: BatchLogoValidationProperties,
  private val imageSearchLogoService: ImageSearchLogoService,
  private val imageDownloadService: ImageDownloadService,
  private val logoValidationService: LogoValidationService,
  private val objectMapper: ObjectMapper,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun validateBatch(holdings: List<EtfHolding>): List<BatchLogoValidationResult> {
    if (holdings.isEmpty()) return emptyList()
    if (!batchProperties.enabled || openRouterProperties.apiKey.isBlank()) {
      log.debug("Batch validation disabled or API key not configured")
      return emptyList()
    }
    val holdingData = prepareHoldingData(holdings)
    if (holdingData.isEmpty()) return emptyList()
    val validIndicesMap = callBatchValidationApi(holdingData)
    return buildResults(holdingData, validIndicesMap)
  }

  private fun prepareHoldingData(holdings: List<EtfHolding>): List<HoldingCandidateData> =
    runBlocking(Dispatchers.IO.limitedParallelism(10)) {
      holdings
        .take(batchProperties.batchSize)
        .map { holding ->
          async {
            val searchQuery = LogoSearchQueryBuilder.buildQuery(holding.name, holding.ticker)
            val candidates =
              imageSearchLogoService.searchLogoCandidates(searchQuery, batchProperties.imagesPerCompany * 3)
            val downloaded = downloadAndValidateCandidates(candidates)
            if (downloaded.isEmpty()) return@async null
            HoldingCandidateData(
              holdingUuid = holding.uuid,
              companyName = holding.name,
              ticker = holding.ticker,
              candidates = downloaded.map { it.first },
              imageData = downloaded.associate { it.first.index to it.second },
            )
          }
        }.awaitAll()
        .filterNotNull()
    }

  private suspend fun CoroutineScope.downloadAndValidateCandidates(candidates: List<LogoCandidate>): List<Pair<LogoCandidate, ByteArray>> =
    candidates
      .take(batchProperties.imagesPerCompany * 2)
      .map { candidate ->
        async {
          withTimeoutOrNull(batchProperties.downloadTimeoutMs) {
            val imageData = downloadImage(candidate.imageUrl) ?: downloadImage(candidate.thumbnailUrl)
            if (imageData == null || !logoValidationService.isValidLogo(imageData)) return@withTimeoutOrNull null
            candidate to imageData
          }
        }
      }.awaitAll()
      .filterNotNull()
      .take(batchProperties.imagesPerCompany)

  private fun downloadImage(url: String): ByteArray? =
    runCatching { imageDownloadService.download(url) }
      .onFailure { log.debug("Failed to download from $url: ${it.message}") }
      .getOrNull()

  private fun callBatchValidationApi(holdingData: List<HoldingCandidateData>): Map<UUID, List<Int>> {
    val contentParts = mutableListOf<OpenRouterVisionRequest.ContentPart>()
    contentParts.add(TextContent(text = buildBatchPrompt(holdingData)))
    holdingData.forEachIndexed { companyIndex, data ->
      contentParts.add(TextContent(text = "\n--- Company ${companyIndex + 1}: ${data.companyName} (${data.ticker ?: "N/A"}) ---"))
      data.candidates.forEachIndexed { imageIndex, candidate ->
        val imageBytes = data.imageData[candidate.index] ?: return@forEachIndexed
        val base64 = Base64.getEncoder().encodeToString(imageBytes)
        val mediaType = logoValidationService.detectMediaType(imageBytes)
        contentParts.add(ImageContent(imageUrl = ImageContent.ImageUrl(url = "data:$mediaType;base64,$base64")))
        contentParts.add(TextContent(text = "C${companyIndex + 1}_I${imageIndex + 1}"))
      }
    }
    val request =
      OpenRouterVisionRequest(
        model = batchProperties.model.modelId,
        messages = listOf(Message(role = "user", content = contentParts)),
        maxTokens = batchProperties.maxTokens,
        temperature = batchProperties.temperature,
      )
    return runBlocking {
      withTimeoutOrNull(batchProperties.apiTimeoutMs) {
        runCatching {
          log.info("Sending batch validation request for ${holdingData.size} companies")
          val response =
            openRouterVisionClient.chatCompletion(
              "Bearer ${openRouterProperties.apiKey}",
              request,
            )
          parseValidationResponse(response.extractContent(), holdingData)
        }.onFailure { log.warn("Batch validation API call failed: ${it.message}") }
          .getOrDefault(emptyMap())
      } ?: run {
        log.warn("Batch validation API call timed out after ${batchProperties.apiTimeoutMs}ms")
        emptyMap()
      }
    }
  }

  private fun buildBatchPrompt(holdingData: List<HoldingCandidateData>): String {
    val companyList =
      holdingData
        .mapIndexed { index, data ->
          "Company ${index + 1}: \"${data.companyName}\" (${data.ticker ?: "N/A"})"
        }.joinToString("\n")
    return """
      |For each company below, identify which images are VALID company logos.
      |A valid logo must be the actual official logo of that specific company, not:
      |- A competitor's logo
      |- A generic icon or placeholder
      |- An unrelated image
      |- A logo of a different company with a similar name
      |
      |Companies:
      |$companyList
      |
      |Images are labeled as C{company_number}_I{image_number}.
      |
      |Reply with JSON only, mapping company numbers to arrays of valid image numbers:
      |{"1": [1, 3], "2": [2], "3": [], ...}
      |
      |If no images are valid for a company, use an empty array.
    """.trimMargin()
  }

  private fun parseValidationResponse(
    response: String?,
    holdingData: List<HoldingCandidateData>,
  ): Map<UUID, List<Int>> {
    if (response.isNullOrBlank()) return emptyMap()
    val jsonStr = extractJson(response)
    if (jsonStr.isNullOrBlank()) return emptyMap()
    return runCatching {
      val mapType =
        objectMapper.typeFactory.constructMapType(
          HashMap::class.java,
          String::class.java,
          List::class.java,
        )
      val parsed: Map<String, List<Int>> = objectMapper.readValue(jsonStr, mapType)
      holdingData
        .mapIndexed { index, data ->
          val companyKey = (index + 1).toString()
          val validIndices =
            parsed[companyKey]?.map { it - 1 }?.filter { it >= 0 && it < data.candidates.size } ?: emptyList()
          data.holdingUuid to validIndices
        }.toMap()
    }.onFailure { log.warn("Failed to parse validation response: ${it.message}") }
      .getOrDefault(emptyMap())
  }

  private fun extractJson(response: String): String? {
    val start = response.indexOf('{')
    val end = response.lastIndexOf('}')
    if (start == -1 || end == -1 || end <= start) return null
    return response.substring(start, end + 1)
  }

  private fun buildResults(
    holdingData: List<HoldingCandidateData>,
    validIndicesMap: Map<UUID, List<Int>>,
  ): List<BatchLogoValidationResult> =
    holdingData.map { data ->
      BatchLogoValidationResult(
        holdingUuid = data.holdingUuid,
        companyName = data.companyName,
        ticker = data.ticker,
        validCandidateIndices = validIndicesMap[data.holdingUuid] ?: emptyList(),
        allCandidates = data.candidates,
        imageData = data.imageData,
      )
    }
}
