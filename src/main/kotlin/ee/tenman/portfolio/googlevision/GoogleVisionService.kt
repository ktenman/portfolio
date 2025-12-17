package ee.tenman.portfolio.googlevision

import jakarta.annotation.Resource
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.io.File
import java.util.*

@Service
class GoogleVisionService(
  @Value("\${vision.enabled:false}") private val visionEnabled: Boolean,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val REGEX = "\\b\\d{3}\\s?[A-Z]{3}\\b"
    val CAR_PLATE_NUMBER_PATTERN = Regex(REGEX, RegexOption.IGNORE_CASE)
    private const val VISION_DISABLED_MESSAGE = "Vision service is disabled. Skipping plate number detection."
    private val VISION_DISABLED_RESPONSE = mapOf("error" to "Vision service is disabled")
  }

  @Resource
  private lateinit var googleVisionClient: GoogleVisionClient

  @Retryable(maxAttempts = 2, backoff = Backoff(delay = 1000))
  fun getPlateNumber(photoFile: File): Map<String, String> {
    if (isVisionDisabled()) {
      return VISION_DISABLED_RESPONSE
    }

    val base64Image = FileToBase64.encodeToBase64(photoFile.readBytes())
    val uuid = UUID.randomUUID()
    return getPlateNumber(base64Image, uuid)
  }

  @Retryable(maxAttempts = 2, backoff = Backoff(delay = 1000))
  fun getPlateNumber(
    base64EncodedImage: String,
    uuid: UUID,
  ): Map<String, String> {
    if (isVisionDisabled()) {
      return VISION_DISABLED_RESPONSE
    }
    MDC.put("uuid", uuid.toString())
    log.debug("Starting plate number detection from image. Image size: {} bytes", base64EncodedImage.toByteArray().size)
    return try {
      val textRequest =
        GoogleVisionApiRequest(
          base64EncodedImage,
          GoogleVisionApiRequest.FeatureType.TEXT_DETECTION,
        )
      val textResponse = googleVisionClient.analyzeImage(textRequest)
      val strings =
        textResponse.textAnnotations
          ?.firstOrNull()
          ?.description
          ?.split("\n")
          ?.toTypedArray()
          ?: emptyArray()
      log.info("Received text detection response with {} text blocks", strings.size)
      val response = mutableMapOf<String, String>()
      for (description in strings) {
        log.debug("Processing text annotation: {}", description)
        CAR_PLATE_NUMBER_PATTERN.find(description)?.let { matchResult ->
          val plateNr = matchResult.value.replace(" ", "").uppercase()
          log.info("Plate number found: {}", plateNr)
          response["plateNumber"] = plateNr
          response["hasCar"] = "true"
          return response
        }
      }
      response["hasCar"] = "false"
      response
    } catch (e: Exception) {
      log.error("Error during plate number detection", e)
      emptyMap()
    } finally {
      MDC.remove("uuid")
    }
  }

  private fun isVisionDisabled(): Boolean =
    (!visionEnabled).also { disabled ->
      log.info(if (disabled) VISION_DISABLED_MESSAGE else "Vision service is enabled.")
    }
}
