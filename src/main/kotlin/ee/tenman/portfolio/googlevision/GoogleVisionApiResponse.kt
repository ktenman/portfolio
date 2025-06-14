package ee.tenman.portfolio.googlevision

data class GoogleVisionApiResponse(
  var labelAnnotations: List<EntityAnnotation>? = null,
  var textAnnotations: List<EntityAnnotation>? = null,
) {
  fun setResponses(responses: List<AnnotateImageResponse>) {
    if (responses.size == 1) {
      responses.first().let {
        this.labelAnnotations = it.labelAnnotations
        this.textAnnotations = it.textAnnotations
      }
    } else {
      throw IllegalArgumentException("Expected exactly one response")
    }
  }

  data class AnnotateImageResponse(
    val labelAnnotations: List<EntityAnnotation>?,
    val textAnnotations: List<EntityAnnotation>?,
  )

  data class EntityAnnotation(
    val description: String,
    val score: Float,
  )
}
