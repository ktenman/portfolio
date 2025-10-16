package ee.tenman.portfolio.googlevision

data class GoogleVisionApiResponse(
  var labelAnnotations: List<EntityAnnotation>? = null,
  var textAnnotations: List<EntityAnnotation>? = null,
) {
  fun setResponses(responses: List<AnnotateImageResponse>) {
    require(responses.size == 1) { "Expected exactly one response" }
    responses.first().let {
      this.labelAnnotations = it.labelAnnotations
      this.textAnnotations = it.textAnnotations
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
