package ee.tenman.portfolio.googlevision

import jakarta.annotation.Resource
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.util.*

@Service
class GoogleVisionService {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    private const val REGEX = "\\b\\d{3}\\s?[A-Z]{3}\\b"
    val CAR_PLATE_NUMBER_PATTERN = Regex(REGEX, RegexOption.IGNORE_CASE)
    private val VEHICLE_LABELS = setOf("vehicle", "car")
  }

  @Resource
  private lateinit var googleVisionClient: GoogleVisionClient

  @Retryable(maxAttempts = 2, backoff = Backoff(delay = 1000))
  fun getPlateNumber(base64EncodedImage: String, uuid: UUID): Map<String, String> {
    MDC.put("uuid", uuid.toString())
    log.debug("Starting plate number detection from image. Image size: {} bytes", base64EncodedImage.toByteArray().size)

    return try {
      log.debug("Encoded image to base64")

      val labelRequest = GoogleVisionApiRequest(
        base64EncodedImage,
        GoogleVisionApiRequest.FeatureType.LABEL_DETECTION
      )
      val labelResponse = googleVisionClient.analyzeImage(labelRequest)
      log.info("Received label detection response: {}", labelResponse)

      val hasVehicleOrCar = labelResponse.labelAnnotations?.any { labelAnnotation ->
          labelAnnotation.description.lowercase().split("\\s+".toRegex())
            .any { VEHICLE_LABELS.contains(it) }
      } == true
      log.debug("Vehicle/car detected: {}", hasVehicleOrCar)

      val response = mutableMapOf<String, String>()
      val hasCar = hasCar(labelResponse.labelAnnotations)
      response["hasCar"] = hasCar

      if (!hasVehicleOrCar) {
        return response
      }

      val textRequest = GoogleVisionApiRequest(
        base64EncodedImage,
        GoogleVisionApiRequest.FeatureType.TEXT_DETECTION
      )
      val textResponse = googleVisionClient.analyzeImage(textRequest)
      val strings = textResponse.textAnnotations
        ?.firstOrNull()
        ?.description
        ?.split("\n")
        ?.toTypedArray()
        ?: emptyArray()
      log.info("Received text detection response: {}", textResponse)

      for (description in strings) {
        log.debug("Processing text annotation: {}", description)
        CAR_PLATE_NUMBER_PATTERN.find(description)?.let { matchResult ->
          val plateNr = matchResult.value.replace(" ", "").uppercase()
          log.debug("Plate number found: {}", plateNr)
          response["plateNumber"] = plateNr
          return response
        }
      }
      response
    } catch (e: Exception) {
      log.error("Error during plate number detection", e)
      emptyMap()
    } finally {
      MDC.remove("uuid")
    }
  }

  private fun hasCar(labelAnnotations: List<GoogleVisionApiResponse.EntityAnnotation>?): String {
    return labelAnnotations?.any { annotation ->
      annotation.description.contains("car", ignoreCase = true) ||
        annotation.description.contains("vehicle", ignoreCase = true)
    }?.toString() ?: "false"
  }
}
