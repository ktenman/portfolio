package ee.tenman.portfolio.googlevision

import com.google.auth.oauth2.GoogleCredentials
import ee.tenman.portfolio.exception.VisionServiceException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

@Service
class VisionAuthenticatorService(
  @Value("\${vision.base64EncodedKey:}") private val base64EncodedKey: String,
  @Value("\${vision.enabled:false}") private val visionEnabled: Boolean,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val credentials: GoogleCredentials? = initializeCredentials()

  private fun initializeCredentials(): GoogleCredentials? {
    if (!visionEnabled) {
      log.info("Vision service is disabled. Skipping credentials initialization.")
      return null
    }

    log.info("Vision service is enabled")

    if (base64EncodedKey.isBlank()) {
      log.info("Vision base64 encoded key is not provided. Skipping credentials initialization.")
      return null
    }

    return try {
      val decodedJsonBytes = FileToBase64.decode(base64EncodedKey)
      ByteArrayInputStream(decodedJsonBytes).use { credentialsStream ->
        GoogleCredentials
          .fromStream(credentialsStream)
          .createScoped(
            listOf(
              "https://www.googleapis.com/auth/cloud-vision",
              "https://www.googleapis.com/auth/cloud-platform",
            ),
          ).also { log.info("Google Vision credentials initialized successfully") }
      }
    } catch (e: Exception) {
      log.error("Failed to initialize Google Vision credentials", e)
      null
    }
  }

  val accessToken: String
    get() =
      try {
        if (!visionEnabled) {
          throw VisionServiceException("Vision service is disabled")
        } else {
          log.info("Getting access token")
        }
        credentials?.refreshIfExpired()
        credentials?.accessToken?.tokenValue?.also {
          log.info("Successfully authorized with Google Vision API")
        } ?: throw VisionServiceException("Google Vision credentials not initialized")
      } catch (e: Exception) {
        log.error("Failed to get access token", e)
        throw e
      }
}
