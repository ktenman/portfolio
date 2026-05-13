package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.VisionModel
import ee.tenman.portfolio.dto.DetectionResult
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest
import ee.tenman.portfolio.openrouter.OpenRouterVisionService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
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
  private val imageProcessingService: ImageProcessingService,
  private val calculationDispatcher: CoroutineDispatcher,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun detectPlateNumber(photoFile: File): DetectionResult {
    val resized = imageProcessingService.resizeForPlateDetection(photoFile.readBytes())
    val base64Image = Base64.getEncoder().encodeToString(resized)
    return detectPlateNumber(base64Image, UUID.randomUUID())
  }

  fun detectPlateNumber(
    base64Image: String,
    uuid: UUID,
  ): DetectionResult {
    log.info("Starting parallel license plate detection for $uuid")
    val providerScope = CoroutineScope(SupervisorJob() + calculationDispatcher)
    val jobs =
      buildList<Deferred<DetectionResult?>> {
        VisionModel.entries.forEach { model ->
          when {
            !model.isOpenRouter ->
              add(providerScope.async { runProvider(model) { googleVisionService.extractText(base64Image) } })
            openRouterProperties.apiKey.isNotBlank() ->
              add(providerScope.async { runProvider(model) { callOpenRouter(model, base64Image) } })
          }
        }
      }
    return try {
      runBlocking(calculationDispatcher) { selectFirstSuccessful(jobs) }
    } finally {
      providerScope.cancel()
    }
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
        log.info("Plate detected by ${result.provider?.modelId}, cancelling remaining jobs")
        remainingJobs.forEach { it.cancel() }
        return result
      }
    }
    log.warn("All providers exhausted, no plate detected")
    return DetectionResult()
  }

  private fun callOpenRouter(
    model: VisionModel,
    base64Image: String,
  ): String? {
    val request = OpenRouterVisionRequest.forLicensePlateExtraction(model.modelId, base64Image)
    return openRouterVisionService.extractText(request)
  }

  private suspend fun runProvider(
    model: VisionModel,
    call: () -> String?,
  ): DetectionResult? =
    runCatching {
      log.info("Attempting detection with ${model.modelId}")
      val startTime = System.currentTimeMillis()
      val response = runInterruptible(Dispatchers.IO) { call() }
      val elapsedMs = System.currentTimeMillis() - startTime
      if (response.isNullOrBlank()) {
        log.info("${model.modelId}: empty response in ${elapsedMs}ms")
        return@runCatching DetectionResult(provider = model)
      }
      val plateNumber = extractPlateNumber(response)
      if (plateNumber != null) {
        log.info("${model.modelId} detected plate: $plateNumber in ${elapsedMs}ms")
        return@runCatching DetectionResult(plateNumber = plateNumber, provider = model)
      }
      log.info("${model.modelId}: response '$response' did not match pattern in ${elapsedMs}ms")
      DetectionResult(provider = model)
    }.getOrElse { e ->
      if (e is CancellationException) throw e
      log.error("${model.modelId} failed", e)
      null
    }

  private fun extractPlateNumber(response: String): String? {
    val match = PLATE_NUMBER_PATTERN.find(response.uppercase()) ?: return null
    return match.groupValues[1] + match.groupValues[2]
  }

  companion object {
    private val PLATE_NUMBER_PATTERN = Regex("\\b(\\d{3})\\s*([A-Z]{3})\\b")
  }
}
