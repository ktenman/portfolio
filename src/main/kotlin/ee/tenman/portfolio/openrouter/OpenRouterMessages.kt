package ee.tenman.portfolio.openrouter

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import ee.tenman.portfolio.domain.AiModel

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenRouterRequest(
  val model: String,
  val messages: List<Message>,
  @JsonProperty("max_tokens")
  val maxTokens: Int = 50,
  val temperature: Double = 0.1,
  @JsonProperty("response_format")
  val responseFormat: ResponseFormat? = null,
  val provider: ProviderPreferences? = null,
) {
  data class Message(
    val role: String,
    val content: String,
  )

  data class ResponseFormat(
    @JsonProperty("json_schema")
    val jsonSchema: JsonSchema,
    val type: String = "json_schema",
  )

  data class JsonSchema(
    val name: String,
    val schema: Map<String, Any>,
    val strict: Boolean = true,
  )

  data class ProviderPreferences(
    @JsonProperty("require_parameters")
    val requireParameters: Boolean = true,
  )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenRouterResponse(
  val choices: List<Choice>? = null,
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Choice(
    val message: Message? = null,
  )

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Message(
    val content: String? = null,
    val reasoning: String? = null,
  )

  fun extractContent(): String? =
    choices
      ?.firstOrNull()
      ?.message
      ?.content
      ?.trim()
      ?.takeIf { it.isNotBlank() }
}

data class OpenRouterVisionRequest(
  val model: String,
  val messages: List<Message>,
  @JsonProperty("max_tokens")
  val maxTokens: Int = 50,
  val temperature: Double = 0.1,
) {
  data class Message(
    val role: String,
    val content: List<ContentPart>,
  )

  sealed class ContentPart

  data class TextContent(
    val type: String = "text",
    val text: String,
  ) : ContentPart()

  data class ImageContent(
    val type: String = "image_url",
    @JsonProperty("image_url")
    val imageUrl: ImageUrl,
  ) : ContentPart() {
    data class ImageUrl(
      val url: String,
    )
  }

  companion object {
    private const val PLATE_EXTRACTION_PROMPT = "License plate number only, no explanation."

    fun forLicensePlateExtraction(
      modelId: String,
      base64Image: String,
    ): OpenRouterVisionRequest =
      OpenRouterVisionRequest(
        model = modelId,
        messages =
          listOf(
            Message(
              role = "user",
              content =
                listOf(
                  TextContent(text = PLATE_EXTRACTION_PROMPT),
                  ImageContent(imageUrl = ImageContent.ImageUrl(url = "data:image/jpeg;base64,$base64Image")),
                ),
            ),
          ),
        maxTokens = 50,
        temperature = 0.1,
      )
  }
}

data class OpenRouterClassificationResult(
  val content: String?,
  val model: AiModel?,
)

data class ModelSelection(
  val model: AiModel,
  val fallbackTier: Int,
) {
  val modelId: String get() = model.modelId
  val isUsingFallback: Boolean get() = fallbackTier > 0
}
