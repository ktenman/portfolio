package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DetectionProvider
import ee.tenman.portfolio.domain.VisionModel
import ee.tenman.portfolio.dto.DetectionResult
import ee.tenman.portfolio.googlevision.GoogleVisionService
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest
import ee.tenman.portfolio.openrouter.OpenRouterVisionService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.UUID

@Service
class LicensePlateDetectionService(
  private val googleVisionService: GoogleVisionService,
  private val openRouterVisionService: OpenRouterVisionService,
  private val openRouterProperties: OpenRouterProperties,
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
    detectPlateNumberAsync(base64Image, uuid)
  }

  private suspend fun detectPlateNumberAsync(
    base64Image: String,
    uuid: UUID,
  ): DetectionResult {
    log.info("Starting parallel license plate detection")
    val jobs = mutableListOf<kotlinx.coroutines.Deferred<DetectionResult?>>()
    val scope =
      kotlinx.coroutines.coroutineScope {
      val googleVisionJob =
        async {
        tryGoogleVision(base64Image, uuid)
      }
      jobs.add(googleVisionJob)
      if (openRouterProperties.apiKey.isNotBlank()) {
        VisionModel.openRouterModels().forEach { model ->
          val job =
            async {
            tryVisionModel(model, base64Image)
          }
          jobs.add(job)
        }
      }
      selectFirstSuccessful(jobs)
    }
    return scope
  }

  private suspend fun selectFirstSuccessful(jobs: List<kotlinx.coroutines.Deferred<DetectionResult?>>): DetectionResult {
    val remainingJobs = jobs.toMutableList()
    var lastHasCar = false
    while (remainingJobs.isNotEmpty()) {
      val result =
        select<DetectionResult?> {
          remainingJobs.forEach { deferred ->
            deferred.onAwait { it }
          }
        }
      val completedJob = remainingJobs.find { it.isCompleted }
      if (completedJob != null) {
        remainingJobs.remove(completedJob)
      }
      if (result != null) {
        if (result.plateNumber != null) {
          log.info("Plate detected by {}, cancelling remaining jobs", result.provider)
          remainingJobs.forEach { it.cancel() }
          return result
        }
        lastHasCar = lastHasCar || result.hasCar
      }
    }
    log.warn("All providers exhausted, no plate detected")
    return DetectionResult(plateNumber = null, hasCar = lastHasCar, provider = DetectionProvider.ALL_FAILED)
  }

  private fun tryGoogleVision(
    base64Image: String,
    uuid: UUID,
  ): DetectionResult? =
    runCatching {
      log.info("Attempting detection with Google Vision")
      val startTime = System.currentTimeMillis()
      val result = googleVisionService.getPlateNumber(base64Image, uuid)
      val elapsedMs = System.currentTimeMillis() - startTime
      val hasCar = result["hasCar"]?.toBoolean() ?: false
      val plateNumber = result["plateNumber"]
      if (plateNumber != null) {
        log.info("Google Vision detected plate: {} in {}ms", plateNumber, elapsedMs)
        return DetectionResult(plateNumber = plateNumber, hasCar = hasCar, provider = DetectionProvider.GOOGLE_VISION)
      }
      log.info("Google Vision: no plate found in {}ms, hasCar={}", elapsedMs, hasCar)
      DetectionResult(plateNumber = null, hasCar = hasCar, provider = DetectionProvider.GOOGLE_VISION)
    }.getOrElse { e ->
      log.error("Google Vision failed: {}", e.message)
      null
    }

  private fun tryVisionModel(
    model: VisionModel,
    base64Image: String,
  ): DetectionResult? =
    runCatching {
      log.info("Attempting detection with {}", model.modelId)
      val startTime = System.currentTimeMillis()
      val request = OpenRouterVisionRequest.forLicensePlateExtraction(model.modelId, base64Image)
      val response = openRouterVisionService.extractText(request)
      val elapsedMs = System.currentTimeMillis() - startTime
      if (response.isNullOrBlank()) {
        log.info("{}: empty response in {}ms", model.modelId, elapsedMs)
        return DetectionResult(plateNumber = null, hasCar = false, provider = DetectionProvider.fromVisionModel(model))
      }
      val plateNumber = extractPlateNumber(response)
      if (plateNumber != null) {
        log.info("{} detected plate: {} in {}ms", model.modelId, plateNumber, elapsedMs)
        return DetectionResult(plateNumber = plateNumber, hasCar = true, provider = DetectionProvider.fromVisionModel(model))
      }
      log.info("{}: response '{}' did not match pattern in {}ms", model.modelId, response, elapsedMs)
      DetectionResult(plateNumber = null, hasCar = false, provider = DetectionProvider.fromVisionModel(model))
    }.getOrElse { e ->
      log.error("{} failed: {}", model.modelId, e.message)
      null
    }

  private fun extractPlateNumber(response: String): String? {
    val cleaned = response.replace("\\s".toRegex(), "").uppercase()
    return if (GoogleVisionService.CAR_PLATE_NUMBER_PATTERN.containsMatchIn(cleaned)) {
      GoogleVisionService.CAR_PLATE_NUMBER_PATTERN.find(cleaned)?.value
    } else {
      null
    }
  }

  private fun encodeFileToBase64(file: File): String =
    java.util.Base64
      .getEncoder()
      .encodeToString(file.readBytes())
}
