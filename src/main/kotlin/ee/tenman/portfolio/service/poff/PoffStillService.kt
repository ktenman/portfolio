package ee.tenman.portfolio.service.poff

import ee.tenman.portfolio.domain.PoffFilm
import ee.tenman.portfolio.service.infrastructure.ImageDownloadService
import ee.tenman.portfolio.service.infrastructure.ImageProcessingService
import ee.tenman.portfolio.service.infrastructure.MinioService
import org.slf4j.LoggerFactory
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

@Service
class PoffStillService(
  private val imageDownloadService: ImageDownloadService,
  private val imageProcessingService: ImageProcessingService,
  private val minioService: MinioService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun store(films: List<PoffFilm>) {
    val stored = minioService.poffStillNames()
    films
      .flatMap { it.stills }
      .distinct()
      .filterNot { name(stillKey(it), WIDTHS.last()) in stored }
      .forEach(::copy)
  }

  fun still(
    key: String,
    width: Int,
  ): ByteArray? {
    if (!KEY.matches(key) || width !in WIDTHS) return null
    return minioService.downloadPoffStill(name(key, width))
  }

  private fun copy(url: String) {
    runCatching {
      val image = imageDownloadService.download(url)
      WIDTHS.forEach { minioService.uploadPoffStill(name(stillKey(url), it), imageProcessingService.resizeToWidth(image, it)) }
    }.onFailure { log.warn("Failed to store PÖFF still $url: ${it.message}") }
  }

  private fun name(
    key: String,
    width: Int,
  ) = "$key-$width"
}
