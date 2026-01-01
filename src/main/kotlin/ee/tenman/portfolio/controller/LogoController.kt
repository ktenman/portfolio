package ee.tenman.portfolio.controller

import ee.tenman.portfolio.service.infrastructure.MinioService
import org.slf4j.LoggerFactory
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/logos")
class LogoController(
  private val minioService: MinioService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @GetMapping("/{uuid}")
  fun getLogo(
    @PathVariable uuid: UUID,
  ): ResponseEntity<ByteArray> {
    log.debug("Fetching logo for holding UUID: $uuid")
    val logoData = minioService.downloadLogo(uuid)
    if (logoData != null) {
      return ResponseEntity
        .ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
        .body(logoData)
    }
    log.debug("Logo not found for holding UUID: $uuid")
    return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
  }
}
