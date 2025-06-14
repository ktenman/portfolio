package ee.tenman.portfolio.googlevision

data class GoogleVisionApiRequest(
  val requests: List<AnnotateImageRequest>,
) {
  constructor(base64EncodedImage: String, vararg featureTypes: FeatureType) : this(
    requests =
      listOf(
        AnnotateImageRequest(
          image = Image(content = base64EncodedImage),
          features =
            featureTypes
              .takeIf { it.isNotEmpty() }
              ?.map { Feature(type = it.name) }
              ?: listOf(
                Feature(FeatureType.LABEL_DETECTION.name),
                Feature(FeatureType.TEXT_DETECTION.name),
              ),
        ),
      ),
  )

  enum class FeatureType {
    LABEL_DETECTION,
    TEXT_DETECTION,
  }

  data class AnnotateImageRequest(
    val image: Image,
    val features: List<Feature>,
  )

  data class Image(
    val content: String, // Base64-encoded image data
  )

  data class Feature(
    val type: String, // Type of detection to perform
  )
}
