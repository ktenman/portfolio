package ee.tenman.portfolio.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.JsonNode

@Service
class GoogleVisionService(
  @Value("\${google.vision.api-key:}") private val apiKey: String,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val restClient = RestClient.create()

  fun extractText(base64Image: String): String? {
    if (apiKey.isBlank()) {
      log.warn("Google Vision API key is not configured")
      return null
    }
    return runCatching {
      val response =
        restClient
          .post()
          .uri("https://vision.googleapis.com/v1/images:annotate?key=$apiKey")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
            mapOf(
              "requests" to
                listOf(
                  mapOf(
                    "image" to mapOf("content" to base64Image),
                    "features" to listOf(mapOf("type" to "TEXT_DETECTION")),
                  ),
                ),
            ),
          ).retrieve()
          .body<JsonNode>()
      response
        ?.path("responses")
        ?.path(0)
        ?.path("textAnnotations")
        ?.path(0)
        ?.path("description")
        ?.textValue()
        ?.takeIf { it.isNotBlank() }
    }.onFailure { log.error("Google Vision call failed", it) }.getOrNull()
  }
}
