package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.DetectionProvider
import ee.tenman.portfolio.domain.VisionModel
import ee.tenman.portfolio.dto.DetectionResult
import ee.tenman.portfolio.googlevision.GoogleVisionService
import ee.tenman.portfolio.openrouter.OpenRouterProperties
import ee.tenman.portfolio.openrouter.OpenRouterVisionRequest
import ee.tenman.portfolio.openrouter.OpenRouterVisionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.UUID

@Service
class LicensePlateDetectionService(
  private val googleVisionService: GoogleVisionService,
  private val openRouterVisionService: OpenRouterVisionService,
  private val openRouterProperties: OpenRouterProperties,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun detectPlateNumber(photoFile: File): DetectionResult {
    val base64Image = encodeFileToBase64(photoFile)
    return detectPlateNumber(base64Image, UUID.randomUUID())
  }

  fun detectPlateNumber(
    base64Image: String,
    uuid: UUID,
  ): DetectionResult {
    val googleResult = tryGoogleVision(base64Image, uuid)
    if (googleResult.plateNumber != null) {
      return googleResult
    }
    return tryOpenRouterFallback(base64Image, googleResult.hasCar)
  }

  private fun tryGoogleVision(
    base64Image: String,
    uuid: UUID,
  ): DetectionResult {
    log.info("Attempting license plate detection with Google Vision")
    val result = googleVisionService.getPlateNumber(base64Image, uuid)
    val hasCar = result["hasCar"]?.toBoolean() ?: false
    val plateNumber = result["plateNumber"]
    if (plateNumber != null) {
      log.info("Google Vision detected plate: {}", plateNumber)
      return DetectionResult(plateNumber = plateNumber, hasCar = hasCar, provider = DetectionProvider.GOOGLE_VISION)
    }
    log.info("Google Vision did not detect plate number, hasCar: {}", hasCar)
    return DetectionResult(plateNumber = null, hasCar = hasCar, provider = DetectionProvider.GOOGLE_VISION)
  }

  private fun tryOpenRouterFallback(
    base64Image: String,
    hasCar: Boolean,
  ): DetectionResult {
    if (openRouterProperties.apiKey.isBlank()) {
      log.warn("OpenRouter API key not configured, skipping fallback")
      return DetectionResult(plateNumber = null, hasCar = hasCar, provider = DetectionProvider.GOOGLE_VISION)
    }
    var currentModel: VisionModel? = VisionModel.primary()
    while (currentModel != null) {
      val plateNumber = tryVisionModel(currentModel, base64Image)
      if (plateNumber != null) {
        val provider = DetectionProvider.fromVisionModel(currentModel)
        return DetectionResult(plateNumber = plateNumber, hasCar = true, provider = provider)
      }
      val nextModel = currentModel.nextFallbackModel()
      if (nextModel != null) {
        log.info("Cascading to next vision model: {}", nextModel.modelId)
      }
      currentModel = nextModel
    }
    log.warn("All vision models exhausted, no plate detected")
    return DetectionResult(plateNumber = null, hasCar = hasCar, provider = DetectionProvider.ALL_FAILED)
  }

  private fun tryVisionModel(
    model: VisionModel,
    base64Image: String,
  ): String? {
    log.info("Attempting license plate detection with {}", model.modelId)
    val request = OpenRouterVisionRequest.forLicensePlateExtraction(model.modelId, base64Image)
    val response = openRouterVisionService.extractText(request)
    if (response.isNullOrBlank()) {
      log.info("Model {} returned empty response", model.modelId)
      return null
    }
    val plateNumber = extractPlateNumber(response)
    if (plateNumber != null) {
      log.info("Model {} detected plate: {}", model.modelId, plateNumber)
      return plateNumber
    }
    log.info("Model {} response '{}' did not match plate pattern", model.modelId, response)
    return null
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
