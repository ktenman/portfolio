package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.VisionModel
import ee.tenman.portfolio.dto.DetectionResult
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest
import ee.tenman.portfolio.openrouter.OpenRouterVisionService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.Base64
import java.util.UUID

@Service
class LicensePlateDetectionService(
  private val openRouterVisionService: OpenRouterVisionService,
  private val openRouterProperties: OpenRouterProperties,
  private val googleVisionService: GoogleVisionService,
  private val calculationDispatcher: CoroutineDispatcher,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun detectPlateNumber(photoFile: File): DetectionResult {
    val base64Image = encodeFileToBase64(photoFile)
    return detectPlateNumber(base64Image, UUID.randomUUID())
  }

  fun detectPlateNumber(
    base64Image: String,
    uuid: UUID,
  ): DetectionResult =
    runBlocking(calculationDispatcher) {
      log.info("Starting parallel license plate detection for $uuid")
      val jobs =
        buildList<Deferred<DetectionResult?>> {
        if (openRouterProperties.apiKey.isNotBlank()) {
          VisionModel.entries.forEach { model ->
            add(async { tryVisionModel(model, base64Image) })
          }
        }
        add(async { tryGoogleVision(base64Image) })
      }
      selectFirstSuccessful(jobs)
    }

  private suspend fun selectFirstSuccessful(jobs: List<Deferred<DetectionResult?>>): DetectionResult {
    val remainingJobs = jobs.toMutableList()
    while (remainingJobs.isNotEmpty()) {
      val completedDeferred =
        select<Deferred<DetectionResult?>> {
          remainingJobs.forEach { deferred ->
            deferred.onAwait { deferred }
          }
        }
      val result = completedDeferred.await()
      remainingJobs.remove(completedDeferred)
      if (result?.plateNumber != null) {
        log.info("Plate detected by ${result.provider}, cancelling remaining jobs")
        remainingJobs.forEach { it.cancel() }
        return result
      }
    }
    log.warn("All providers exhausted, no plate detected")
    return DetectionResult()
  }

  private fun tryVisionModel(
    model: VisionModel,
    base64Image: String,
  ): DetectionResult? =
    runCatching {
      log.info("Attempting detection with ${model.modelId}")
      val startTime = System.currentTimeMillis()
      val request = OpenRouterVisionRequest.forLicensePlateExtraction(model.modelId, base64Image)
      val response = openRouterVisionService.extractText(request)
      val elapsedMs = System.currentTimeMillis() - startTime
      if (response.isNullOrBlank()) {
        log.info("${model.modelId}: empty response in ${elapsedMs}ms")
        return@runCatching DetectionResult(provider = model.modelId)
      }
      val plateNumber = extractPlateNumber(response)
      if (plateNumber != null) {
        log.info("${model.modelId} detected plate: $plateNumber in ${elapsedMs}ms")
        return@runCatching DetectionResult(plateNumber = plateNumber, provider = model.modelId)
      }
      log.info("${model.modelId}: response '$response' did not match pattern in ${elapsedMs}ms")
      DetectionResult(provider = model.modelId)
    }.getOrElse { e ->
      log.error("${model.modelId} failed", e)
      null
    }

  private fun tryGoogleVision(base64Image: String): DetectionResult? =
    runCatching {
      log.info("Attempting detection with google-vision")
      val startTime = System.currentTimeMillis()
      val response = googleVisionService.extractText(base64Image)
      val elapsedMs = System.currentTimeMillis() - startTime
      if (response.isNullOrBlank()) {
        log.info("google-vision: empty response in ${elapsedMs}ms")
        return@runCatching DetectionResult(provider = GOOGLE_VISION)
      }
      val plateNumber = extractPlateNumber(response)
      if (plateNumber != null) {
        log.info("google-vision detected plate: $plateNumber in ${elapsedMs}ms")
        return@runCatching DetectionResult(plateNumber = plateNumber, provider = GOOGLE_VISION)
      }
      log.info("google-vision: response '$response' did not match pattern in ${elapsedMs}ms")
      DetectionResult(provider = GOOGLE_VISION)
    }.getOrElse { e ->
      log.error("google-vision failed", e)
      null
    }

  private fun extractPlateNumber(response: String): String? {
    val cleaned = response.replace(WHITESPACE, "").uppercase()
    return PLATE_NUMBER_PATTERN.find(cleaned)?.value
  }

  private fun encodeFileToBase64(file: File): String = Base64.getEncoder().encodeToString(file.readBytes())

  companion object {
    private const val GOOGLE_VISION = "google-vision"
    private val PLATE_NUMBER_PATTERN = Regex("\\b\\d{3}[A-Z]{3}\\b", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s")
  }
}
