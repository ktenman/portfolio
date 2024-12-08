package ee.tenman.portfolio.googlevision

import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

@Service
class VisionAuthenticatorService(
  @Value("\${vision.base64EncodedKey}") private val base64EncodedKey: String
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val credentials: GoogleCredentials? = initializeCredentials()

  private fun initializeCredentials(): GoogleCredentials? {
    if (base64EncodedKey.isBlank()) {
      log.error("VISION_BASE64_ENCODED_KEY environment variable is not set")
      return null
    }

    return try {
      val decodedJsonBytes = FileToBase64.decode(base64EncodedKey)
      ByteArrayInputStream(decodedJsonBytes).use { credentialsStream ->
        GoogleCredentials.fromStream(credentialsStream)
          .createScoped(listOf(
            "https://www.googleapis.com/auth/cloud-vision",
            "https://www.googleapis.com/auth/cloud-platform"
          ))
          .also { log.info("Google Vision credentials initialized successfully") }
      }
    } catch (e: Exception) {
      log.error("Failed to initialize Google Vision credentials", e)
      null
    }
  }

  val accessToken: String
    get() = try {
      credentials?.refreshIfExpired()
      credentials?.accessToken?.tokenValue
        ?: throw RuntimeException("Google Vision credentials not initialized")
    } catch (e: Exception) {
      log.error("Failed to get access token", e)
      throw e
    }
}
