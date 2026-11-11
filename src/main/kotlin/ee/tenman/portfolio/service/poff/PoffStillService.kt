package ee.tenman.portfolio.service.poff

import ee.tenman.portfolio.domain.PoffFilm
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest

private val WIDTHS = listOf(640, 1280)
private val KEY = Regex("[0-9a-f]{20}")

fun stillKey(url: String): String =
  MessageDigest
    .getInstance("SHA-256")
    .digest(url.toByteArray())
    .toHexString()
    .take(20)

fun posterKey(trailer: String): String = stillKey("youtube:$trailer")

@Service
class PoffStillService(
  private val imageDownloadService: ImageDownloadService,
  private val imageProcessingService: ImageProcessingService,
  private val minioService: MinioService,
  @Value("\${youtube.thumbnail-url}") private val thumbnailUrl: String,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun store(films: List<PoffFilm>) {
    val stored = minioService.poffStillNames()
    val stills = films.flatMap { it.stills }.map { stillKey(it) to listOf(it) }
    val posters = films.mapNotNull { film -> film.trailer?.let { posterKey(it) to listOf(thumbnail(it)) + film.stills.take(1) } }
    (stills + posters)
      .distinctBy { it.first }
      .filterNot { name(it.first, WIDTHS.last()) in stored }
      .forEach { (key, sources) -> copy(key, sources) }
  }

  fun still(
    key: String,
    width: Int,
  ): ByteArray? {
    if (!KEY.matches(key) || width !in WIDTHS) return null
    return minioService.downloadPoffStill(name(key, width))
  }

  private fun copy(
    key: String,
    sources: List<String>,
  ) {
    val image =
      sources.firstNotNullOfOrNull(imageDownloadService::downloadOrNull)
        ?: return log.warn("Failed to download PÖFF image $key from any of $sources")
    runCatching { WIDTHS.forEach { minioService.uploadPoffStill(name(key, it), imageProcessingService.resizeToWidth(image, it)) } }
      .onFailure { log.warn("Failed to store PÖFF image $key: ${it.message}") }
  }

  private fun thumbnail(trailer: String) = "$thumbnailUrl/vi/$trailer/maxresdefault.jpg"

  private fun name(
    key: String,
    width: Int,
  ) = "$key-$width"
}
