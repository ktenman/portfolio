package ee.tenman.portfolio.controller

import ee.tenman.portfolio.service.infrastructure.MinioService
import ee.tenman.portfolio.util.LogSanitizerUtil
import org.slf4j.LoggerFactory
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/logos")
class LogoController(
  private val minioService: MinioService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @GetMapping("/{symbol}")
  fun getLogo(
    @PathVariable symbol: String,
  ): ResponseEntity<ByteArray> {
    log.debug("Fetching logo for symbol: ${LogSanitizerUtil.sanitize(symbol)}")

    val logoData = minioService.downloadLogo(symbol)

    return if (logoData != null) {
      ResponseEntity
        .ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
        .body(logoData)
    } else {
      log.debug("Logo not found for symbol: ${LogSanitizerUtil.sanitize(symbol)}")
      ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }
  }
}
